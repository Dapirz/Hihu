package app.hihu.state

import android.content.ContentValues
import app.hihu.music.Song
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The songs the user has hearted, newest first, kept in the database. [onToggle] hears every
 * heart the user gives or takes away, after it is saved.
 */
class Favorites(
    private val store: Store,
    private val scope: CoroutineScope,
    private val onToggle: (song: Song, liked: Boolean) -> Unit = { _, _ -> },
) {
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _ids = MutableStateFlow<Set<String>>(emptySet())

    /** The ids of [songs], for showing a filled heart without searching the list. */
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    /** Done once the hearts are first read, so a tap right at start-up knows which way it goes. */
    private val loaded = CompletableDeferred<Unit>()

    init {
        scope.launch {
            reload()
            loaded.complete(Unit)
        }
    }

    /** Hearts [song], or takes the heart away if it already has one. */
    fun toggle(song: Song) {
        scope.launch {
            loaded.await()
            val adding = song.id !in _ids.value
            _ids.value = if (adding) _ids.value + song.id else _ids.value - song.id
            store.transaction { db ->
                when (adding) {
                    true -> {
                        db.putSong(song)
                        val values = ContentValues().apply {
                            put("song_id", song.id)
                            put("added_at", System.currentTimeMillis())
                        }
                        db.insertWithOnConflict("favorites", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE)
                    }
                    false -> db.delete("favorites", "song_id = ?", arrayOf(song.id))
                }
            }
            reload()
            onToggle(song, adding)
        }
    }

    private suspend fun read(): List<Song> = store.run { db ->
        db.rawQuery(
            "SELECT $SONG_COLUMNS FROM favorites f JOIN songs s ON s.id = f.song_id ORDER BY f.added_at DESC",
            null,
        ).all { it.readSong() }
    }

    private suspend fun reload() {
        val songs = read()
        _songs.value = songs
        _ids.value = songs.mapTo(HashSet(), Song::id)
    }
}
