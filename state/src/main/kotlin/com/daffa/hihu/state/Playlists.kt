package com.daffa.hihu.state

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.daffa.hihu.music.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** A playlist as the library lists it: its name, how many songs, and the first song's cover. */
data class PlaylistSummary(val id: Long, val name: String, val count: Int, val artworkUri: String?)

/**
 * The user's own playlists, kept on the device. These are Hihu's playlists; the ones in a
 * YouTube Music account come with signing in. Songs keep the order they were added in until
 * moved.
 */
class Playlists(private val store: Store, private val scope: CoroutineScope) {
    private val _all = MutableStateFlow<List<PlaylistSummary>>(emptyList())
    val all: StateFlow<List<PlaylistSummary>> = _all.asStateFlow()

    private val contents = MutableStateFlow<Map<Long, List<Song>>>(emptyMap())

    init {
        scope.launch { reloadAll() }
    }

    /** The songs of playlist [id], in order; null until they have been read. */
    fun songs(id: Long): Flow<List<Song>?> {
        if (id !in contents.value) scope.launch { reload(id) }
        return contents.map { it[id] }
    }

    /** Makes a playlist called [name] holding [songs], and passes its id to [created]. */
    fun create(name: String, songs: List<Song> = emptyList(), created: (Long) -> Unit = {}) {
        scope.launch { created(createNow(name, songs)) }
    }

    /** Like [create], for callers already in a coroutine: returns the new playlist's id once saved. */
    suspend fun createNow(name: String, songs: List<Song>): Long {
        val id = store.transaction { db ->
            val id = db.insert("playlists", null, ContentValues().apply {
                put("name", name.trim())
                put("created_at", System.currentTimeMillis())
            })
            db.append(id, songs)
            id
        }
        reload(id)
        reloadAll()
        return id
    }

    fun rename(id: Long, name: String) = change(id) { db ->
        db.update("playlists", ContentValues().apply { put("name", name.trim()) }, "id = ?", arrayOf(id.toString()))
    }

    fun delete(id: Long) {
        scope.launch {
            store.run { db -> db.delete("playlists", "id = ?", arrayOf(id.toString())) }
            contents.value -= id
            reloadAll()
        }
    }

    /** Adds [songs] to the end of playlist [id], skipping the ones already in it. */
    fun add(id: Long, songs: List<Song>) = change(id) { db -> db.append(id, songs) }

    fun remove(id: Long, songId: String) = change(id) { db ->
        db.delete("playlist_songs", "playlist_id = ? AND song_id = ?", arrayOf(id.toString(), songId))
        db.renumber(id, db.order(id))
    }

    /** Moves the song at [from] to [to], both counted from the top. */
    fun move(id: Long, from: Int, to: Int) = change(id) { db ->
        val order = db.order(id).toMutableList()
        if (from !in order.indices || to !in order.indices) return@change
        order.add(to, order.removeAt(from))
        db.renumber(id, order)
    }

    /** Applies [block] to playlist [id] in a transaction, then refreshes what is shown. */
    private fun change(id: Long, block: (SQLiteDatabase) -> Unit) {
        scope.launch {
            store.transaction(block)
            reload(id)
            reloadAll()
        }
    }

    private suspend fun reload(id: Long) {
        val songs = store.run { db ->
            db.rawQuery(
                "SELECT $SONG_COLUMNS FROM playlist_songs p JOIN songs s ON s.id = p.song_id WHERE p.playlist_id = ? ORDER BY p.position",
                arrayOf(id.toString()),
            ).all { it.readSong() }
        }
        contents.value += id to songs
    }

    private suspend fun reloadAll() {
        _all.value = store.run { db ->
            db.rawQuery(
                """
                SELECT p.id, p.name, COUNT(ps.song_id),
                    (SELECT s.artwork_uri FROM playlist_songs x JOIN songs s ON s.id = x.song_id
                     WHERE x.playlist_id = p.id ORDER BY x.position LIMIT 1)
                FROM playlists p LEFT JOIN playlist_songs ps ON ps.playlist_id = p.id
                GROUP BY p.id ORDER BY p.created_at DESC
                """,
                null,
            ).all { PlaylistSummary(it.getLong(0), it.getString(1), it.getInt(2), it.getString(3)) }
        }
    }
}

/** Song ids of playlist [id], in order. */
private fun SQLiteDatabase.order(id: Long): List<String> =
    rawQuery("SELECT song_id FROM playlist_songs WHERE playlist_id = ? ORDER BY position", arrayOf(id.toString()))
        .all { it.getString(0) }

/** Writes positions 0, 1, 2… in the order of [songIds]. */
private fun SQLiteDatabase.renumber(id: Long, songIds: List<String>) {
    songIds.forEachIndexed { position, songId ->
        update(
            "playlist_songs",
            ContentValues().apply { put("position", position) },
            "playlist_id = ? AND song_id = ?",
            arrayOf(id.toString(), songId),
        )
    }
}

/** Adds [songs] after the last one in playlist [id], ignoring the ones already there. */
private fun SQLiteDatabase.append(id: Long, songs: List<Song>) {
    var next = rawQuery("SELECT COALESCE(MAX(position) + 1, 0) FROM playlist_songs WHERE playlist_id = ?", arrayOf(id.toString()))
        .all { it.getInt(0) }.first()
    songs.forEach { song ->
        putSong(song)
        val values = ContentValues().apply {
            put("playlist_id", id)
            put("song_id", song.id)
            put("position", next)
        }
        if (insertWithOnConflict("playlist_songs", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L) next++
    }
}
