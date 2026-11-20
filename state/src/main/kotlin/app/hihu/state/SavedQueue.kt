package app.hihu.state

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import app.hihu.music.Song

/** The queue as it was last saved: its songs, which one was playing, and how far in. */
data class QueueSnapshot(val songs: List<Song>, val index: Int, val positionMs: Long)

/** Saves the play queue to the database so it is still there after the app is closed. */
class SavedQueue(private val store: Store) {

    suspend fun save(snapshot: QueueSnapshot) = store.transaction { db ->
        db.delete("queue", null, null)
        snapshot.songs.forEachIndexed { position, song ->
            db.putSong(song)
            db.insert("queue", null, ContentValues().apply {
                put("position", position)
                put("song_id", song.id)
            })
        }
        db.putState(KEY_INDEX, snapshot.index.toLong())
        db.putState(KEY_POSITION, snapshot.positionMs)
    }

    /** Updates only where in the queue playback is, which changes far more often than the queue. */
    suspend fun savePosition(index: Int, positionMs: Long) = store.transaction { db ->
        db.putState(KEY_INDEX, index.toLong())
        db.putState(KEY_POSITION, positionMs)
    }

    /** The saved queue, or null when there is none. */
    suspend fun load(): QueueSnapshot? = store.run { db ->
        val songs = db.rawQuery("SELECT $SONG_COLUMNS FROM queue q JOIN songs s ON s.id = q.song_id ORDER BY q.position", null)
            .all { it.readSong() }
        if (songs.isEmpty()) return@run null
        val index = db.state(KEY_INDEX).toInt().coerceIn(songs.indices)
        QueueSnapshot(songs, index, db.state(KEY_POSITION).coerceAtLeast(0))
    }

    private fun SQLiteDatabase.putState(key: String, value: Long) {
        insertWithOnConflict(
            "queue_state",
            null,
            ContentValues().apply {
                put("key", key)
                put("value", value)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun SQLiteDatabase.state(key: String): Long =
        rawQuery("SELECT value FROM queue_state WHERE key = ?", arrayOf(key)).all { it.getLong(0) }.firstOrNull() ?: 0

    private companion object {
        const val KEY_INDEX = "index"
        const val KEY_POSITION = "position_ms"
    }
}
