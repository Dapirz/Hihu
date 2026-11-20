package app.hihu.state

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import android.util.LruCache
import app.hihu.music.Lyrics
import app.hihu.music.LyricsProvider
import app.hihu.music.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The lyrics of the song playing now, and how fetching them is going. [offsetMs] moves synced
 * lines for this song: positive shows each line that much later, negative earlier.
 */
data class LyricsState(
    val songId: String? = null,
    val lyrics: Lyrics? = null,
    val loading: Boolean = false,
    val missing: Boolean = false,
    val failed: Boolean = false,
    val offsetMs: Long = 0,
)

/**
 * Fetches the lyrics of the song playing, but only while the lyrics are on screen ([setVisible]),
 * so nothing about songs whose lyrics are never opened leaves the phone. Each song is fetched
 * once; a song played again comes from memory, and so does finding none. The timing the user
 * sets for a song's synced lines with [shift] is kept in the database and comes back with it.
 */
class SongLyrics(
    private val source: LyricsProvider,
    private val playback: Playback,
    private val store: Store,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(LyricsState())
    val state: StateFlow<LyricsState> = _state.asStateFlow()

    private val cache = LruCache<String, Found>(MAX_CACHED)
    private var job: Job? = null
    private var saving: Job? = null

    private class Found(val lyrics: Lyrics?)

    private val visible = MutableStateFlow(false)

    init {
        scope.launch {
            val song = playback.state.map { it.song }.distinctUntilChanged { a, b -> a?.id == b?.id }
            combine(song, visible) { s, shown -> s to shown }.collect { (s, shown) ->
                if (shown && s?.id != _state.value.songId) load(s)
            }
        }
    }

    /** Tells whether the lyrics are on screen; they are only fetched while they are. */
    fun setVisible(shown: Boolean) {
        visible.value = shown
    }

    /** Tries again for the song playing now, after fetching failed. */
    fun retry() {
        val song = playback.state.value.song ?: return
        cache.remove(song.id)
        load(song)
    }

    /** Moves the synced lines of the song shown by [deltaMs], within [MAX_OFFSET_MS] either way. */
    fun shift(deltaMs: Long) = setOffset(_state.value.offsetMs + deltaMs)

    /** Puts the song's lines back at the times the lyrics came with. */
    fun resetOffset() = setOffset(0)

    private fun setOffset(offsetMs: Long) {
        val now = _state.value
        val id = now.songId ?: return
        val offset = offsetMs.coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS)
        _state.value = now.copy(offsetMs = offset)
        // Only the last of a run of taps is written.
        saving?.cancel()
        saving = scope.launch {
            delay(SAVE_DELAY_MS)
            try {
                store.run { db ->
                    when (offset == 0L) {
                        true -> db.delete("lyrics_offsets", "song_id = ?", arrayOf(id))
                        false -> db.insertWithOnConflict(
                            "lyrics_offsets",
                            null,
                            ContentValues().apply {
                                put("song_id", id)
                                put("offset_ms", offset)
                            },
                            SQLiteDatabase.CONFLICT_REPLACE,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "lyrics: cannot save the timing", e)
            }
        }
    }

    private suspend fun offsetOf(songId: String): Long = runCatching {
        store.run { db ->
            db.rawQuery("SELECT offset_ms FROM lyrics_offsets WHERE song_id = ?", arrayOf(songId))
                .all { it.getLong(0) }.firstOrNull() ?: 0
        }
    }.getOrDefault(0)

    private fun load(song: Song?) {
        job?.cancel()
        if (song == null) {
            _state.value = LyricsState()
            return
        }
        val cached = cache.get(song.id)
        _state.value = when (cached) {
            null -> LyricsState(song.id, loading = true)
            else -> LyricsState(song.id, cached.lyrics, missing = cached.lyrics == null)
        }
        job = scope.launch {
            val offset = offsetOf(song.id)
            if (cached != null) {
                _state.value = _state.value.copy(offsetMs = offset)
                return@launch
            }
            try {
                val lyrics = source.lyrics(withLength(song))
                cache.put(song.id, Found(lyrics))
                _state.value = LyricsState(song.id, lyrics, missing = lyrics == null, offsetMs = offset)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "lyrics: cannot fetch lyrics", e)
                _state.value = LyricsState(song.id, failed = true, offsetMs = offset)
            }
        }
    }

    /**
     * [song] with its length. Songs from feeds often come without one, and synced lyrics are only
     * used when the length matches, so this waits a moment for the player to know it.
     */
    private suspend fun withLength(song: Song): Song {
        if (song.durationMs > 0) return song
        val known = withTimeoutOrNull(LENGTH_WAIT_MS) {
            playback.state.first { it.songId == song.id && it.durationMs > 0 }.durationMs
        } ?: return song
        return song.copy(durationMs = known)
    }

    private companion object {
        const val LENGTH_WAIT_MS = 5_000L
        const val TAG = "Hihu"
        const val MAX_CACHED = 50
        const val MAX_OFFSET_MS = 30_000L
        const val SAVE_DELAY_MS = 1_000L
    }
}
