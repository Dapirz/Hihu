package com.daffa.hihu.state

import android.content.ContentValues
import com.daffa.hihu.music.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What has been played, most recent first and each song once. Old entries are pruned. */
class History(private val store: Store, private val scope: CoroutineScope) {
    private val _recent = MutableStateFlow<List<Song>>(emptyList())
    val recent: StateFlow<List<Song>> = _recent.asStateFlow()

    init {
        scope.launch { reload() }
    }

    /** Notes that [song] started playing now. */
    fun record(song: Song) {
        scope.launch {
            store.transaction { db ->
                db.putSong(song)
                db.insert("history", null, ContentValues().apply {
                    put("song_id", song.id)
                    put("played_at", System.currentTimeMillis())
                })
                db.execSQL(
                    "DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY played_at DESC LIMIT $KEEP_ROWS)",
                )
            }
            reload()
        }
    }

    private suspend fun reload() {
        _recent.value = store.run { db ->
            db.rawQuery(
                """
                SELECT $SONG_COLUMNS FROM songs s
                JOIN (SELECT song_id, MAX(played_at) AS last FROM history GROUP BY song_id) h ON h.song_id = s.id
                ORDER BY h.last DESC LIMIT $SHOWN
                """,
                null,
            ).all { it.readSong() }
        }
    }

    private companion object {
        const val SHOWN = 100
        const val KEEP_ROWS = 1000
    }
}
