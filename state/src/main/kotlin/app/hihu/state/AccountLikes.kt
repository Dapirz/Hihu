package app.hihu.state

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import app.hihu.music.MusicAccount
import app.hihu.music.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Sends the hearts the user gives or takes away while signed in to the account's likes, one way:
 * likes made elsewhere never come into Hihu, and hearts given while signed out are never sent.
 * Each change goes through a small queue, so one made offline reaches the account later; queued
 * changes go out one at a time with a pause between them, as a person tapping would. When the
 * account cannot be reached, the queue is tried again after a wait that grows each time.
 */
class AccountLikes(
    private val source: MusicAccount,
    private val status: StateFlow<AccountStatus>,
    private val store: Store,
    private val scope: CoroutineScope,
) {
    private var job: Job? = null
    private var retry: Job? = null
    private var again = false
    private var waitMs = FIRST_RETRY_MS

    init {
        scope.launch {
            var previous: AccountStatus? = null
            status.collect { now ->
                val was = previous
                previous = now
                when {
                    now is AccountStatus.SignedIn -> send()
                    // Only a real sign-out or expiry; the first SignedOut is the app starting.
                    was != null && (now == AccountStatus.SignedOut || now == AccountStatus.Expired) -> forget()
                }
            }
        }
    }

    /** Called for every heart the user changes; queues it for the account while signed in. */
    fun toggled(song: Song, liked: Boolean) {
        if (!source.canLike(song)) return
        when (status.value) {
            AccountStatus.SignedOut, AccountStatus.Expired -> return
            else -> Unit
        }
        scope.launch {
            val values = ContentValues().apply {
                put("song_id", song.id)
                put("liked", if (liked) 1 else 0)
                put("queued_at", System.currentTimeMillis())
            }
            // A later tap on the same song replaces the earlier one, so only the last state goes out.
            store.run { db -> db.insertWithOnConflict("like_queue", null, values, SQLiteDatabase.CONFLICT_REPLACE) }
            if (status.value is AccountStatus.SignedIn) send()
        }
    }

    /**
     * Sends the queue in order; stops at the first failure and tries again later. A call while it
     * is being sent makes it look once more when it is done, for a tap that came in at the end.
     */
    private fun send() {
        if (job?.isActive == true) {
            again = true
            return
        }
        again = false
        retry?.cancel()
        job = scope.launch {
            try {
                var first = true
                while (true) {
                    val next = store.run { db ->
                        db.rawQuery(
                            "SELECT $SONG_COLUMNS, q.liked FROM like_queue q JOIN songs s ON s.id = q.song_id ORDER BY q.queued_at LIMIT 1",
                            null,
                        ).all { it.readSong() to (it.getInt(SONG_COLUMN_COUNT) == 1) }.firstOrNull()
                    } ?: break
                    if (!first) delay(PAUSE_MS)
                    first = false
                    val (song, liked) = next
                    source.setLiked(song, liked)
                    // Only if no newer tap replaced it while this one was on its way.
                    store.run { db -> db.delete("like_queue", "song_id = ? AND liked = ?", arrayOf(song.id, if (liked) "1" else "0")) }
                }
                waitMs = FIRST_RETRY_MS
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "likes: cannot reach the account, trying again in ${waitMs / 1000} s", e)
                val wait = waitMs
                waitMs = (waitMs * 2).coerceAtMost(MAX_RETRY_MS)
                retry = scope.launch {
                    delay(wait)
                    if (status.value is AccountStatus.SignedIn) send()
                }
                return@launch
            }
            if (again) scope.launch { send() }
        }
    }

    /** Drops what was waiting to be sent, after signing out; the hearts stay. */
    private fun forget() {
        job?.cancel()
        retry?.cancel()
        again = false
        waitMs = FIRST_RETRY_MS
        scope.launch { store.run { db -> db.delete("like_queue", null, null) } }
    }

    private companion object {
        const val TAG = "Hihu"
        const val PAUSE_MS = 2_000L
        const val FIRST_RETRY_MS = 60_000L
        const val MAX_RETRY_MS = 30 * 60_000L
        // The queue's `liked` column comes right after the song's own columns.
        const val SONG_COLUMN_COUNT = 7
    }
}
