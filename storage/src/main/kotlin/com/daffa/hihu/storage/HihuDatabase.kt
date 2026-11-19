package com.daffa.hihu.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * The one SQLite file, `hihu.db`, on the platform's own SQLite so no database library ships in
 * the app. This class owns the schema and its upgrades; the queries live in `state`.
 *
 * `songs` keeps a copy of every song the other tables point at, so a favourite or a playlist
 * still shows its title and cover when the provider is offline.
 */
class HihuDatabase(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        SCHEMA.forEach(db::execSQL)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 is the first; later versions add their migrations here, oldest first.
    }

    private companion object {
        const val NAME = "hihu.db"
        const val VERSION = 1

        val SCHEMA = listOf(
            """
            CREATE TABLE songs (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                artist TEXT,
                album TEXT,
                duration_ms INTEGER NOT NULL,
                uri TEXT NOT NULL,
                artwork_uri TEXT
            )
            """,
            """
            CREATE TABLE favorites (
                song_id TEXT PRIMARY KEY REFERENCES songs(id),
                added_at INTEGER NOT NULL
            )
            """,
            """
            CREATE TABLE playlists (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """,
            """
            CREATE TABLE playlist_songs (
                playlist_id INTEGER NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
                song_id TEXT NOT NULL REFERENCES songs(id),
                position INTEGER NOT NULL,
                PRIMARY KEY (playlist_id, song_id)
            )
            """,
            "CREATE INDEX playlist_songs_order ON playlist_songs(playlist_id, position)",
            """
            CREATE TABLE history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                song_id TEXT NOT NULL REFERENCES songs(id),
                played_at INTEGER NOT NULL
            )
            """,
            "CREATE INDEX history_recent ON history(played_at)",
            """
            CREATE TABLE queue (
                position INTEGER PRIMARY KEY,
                song_id TEXT NOT NULL REFERENCES songs(id)
            )
            """,
            """
            CREATE TABLE queue_state (
                key TEXT PRIMARY KEY,
                value INTEGER NOT NULL
            )
            """,
        )
    }
}
