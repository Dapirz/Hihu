package app.hihu.state

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import app.hihu.music.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The songs the user asked never to hear again, newest first, kept in the database. The radio,
 * autoplay and Home's rows leave them out; a song the user picks from their own list still plays.
 * [onChange] hears each song hidden or shown again, after it is saved.
 */
class HiddenSongs(
    private val store: Store,
    private val scope: CoroutineScope,
    private val onChange: (song: Song, hidden: Boolean) -> Unit = { _, _ -> },
) {
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _ids = MutableStateFlow<Set<String>>(emptySet())

    /** The ids of [songs], read when filling a queue. */
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    init {
        scope.launch { reload() }
    }

    /** Hides [song] from now on. */
    fun hide(song: Song) = change(song, hidden = true)

    /** Lets [song] come up again. */
    fun show(song: Song) = change(song, hidden = false)

    private fun change(song: Song, hidden: Boolean) {
        _ids.value = when (hidden) {
            true -> _ids.value + song.id
            false -> _ids.value - song.id
        }
        scope.launch {
            store.transaction { db ->
                when (hidden) {
                    true -> {
                        db.putSong(song)
                        val values = ContentValues().apply {
                            put("song_id", song.id)
                            put("hidden_at", System.currentTimeMillis())
                        }
                        db.insertWithOnConflict("hidden_songs", null, values, SQLiteDatabase.CONFLICT_IGNORE)
                    }
                    false -> db.delete("hidden_songs", "song_id = ?", arrayOf(song.id))
                }
            }
            reload()
            onChange(song, hidden)
        }
    }

    private suspend fun reload() {
        val songs = store.run { db ->
            db.rawQuery(
                "SELECT $SONG_COLUMNS FROM hidden_songs h JOIN songs s ON s.id = h.song_id ORDER BY h.hidden_at DESC",
                null,
            ).all { it.readSong() }
        }
        _songs.value = songs
        _ids.value = songs.mapTo(HashSet(), Song::id)
    }
}
