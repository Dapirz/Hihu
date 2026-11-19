package com.daffa.hihu.state

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.daffa.hihu.music.Song
import com.daffa.hihu.storage.HihuDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

/**
 * The app's database for the state holders. Every call runs on one background thread, so
 * reads never see a half-finished write and the main thread never touches the disk.
 */
class Store(context: Context) {
    private val helper = HihuDatabase(context)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val thread = Dispatchers.IO.limitedParallelism(1)

    /** Runs [block] against the database on the store's thread. */
    internal suspend fun <T> run(block: (SQLiteDatabase) -> T): T = withContext(thread) {
        block(helper.writableDatabase)
    }

    /** Runs [block] inside one transaction, which is rolled back if it throws. */
    internal suspend fun <T> transaction(block: (SQLiteDatabase) -> T): T = run { db ->
        db.beginTransaction()
        try {
            block(db).also { db.setTransactionSuccessful() }
        } finally {
            db.endTransaction()
        }
    }
}

/** Saves or refreshes the copy of [song] that other tables point at. */
internal fun SQLiteDatabase.putSong(song: Song) {
    val values = ContentValues().apply {
        put("id", song.id)
        put("title", song.title)
        put("artist", song.artist)
        put("album", song.album)
        put("duration_ms", song.durationMs)
        put("uri", song.uri)
        put("artwork_uri", song.artworkUri)
    }
    insertWithOnConflict("songs", null, values, SQLiteDatabase.CONFLICT_REPLACE)
}

/** The song columns, prefixed with `s.`, in the order [readSong] expects. */
internal const val SONG_COLUMNS = "s.id, s.title, s.artist, s.album, s.duration_ms, s.uri, s.artwork_uri"

/** Reads a [Song] from the row the cursor is on, starting at column [from]. */
internal fun Cursor.readSong(from: Int = 0) = Song(
    id = getString(from),
    title = getString(from + 1),
    artist = getString(from + 2),
    album = getString(from + 3),
    durationMs = getLong(from + 4),
    uri = getString(from + 5),
    artworkUri = getString(from + 6),
)

/** Every row of the cursor, mapped with [read], closing the cursor afterwards. */
internal fun <T> Cursor.all(read: (Cursor) -> T): List<T> = use {
    buildList(count) { while (moveToNext()) add(read(this@all)) }
}
