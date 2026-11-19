package com.daffa.hihu.music.local

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.daffa.hihu.music.Song
import com.daffa.hihu.music.SongLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the music files Android has indexed in MediaStore. Needs READ_MEDIA_AUDIO (or
 * READ_EXTERNAL_STORAGE below Android 13); without it the query returns nothing.
 */
class MediaStoreLibrary(private val context: Context) : SongLibrary {

    override suspend fun songs(): List<Song> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val order = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        context.contentResolver.query(collection, projection, selection, null, order)?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val duration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            buildList(c.count) {
                while (c.moveToNext()) {
                    val rowId = c.getLong(id)
                    val uri = ContentUris.withAppendedId(collection, rowId).toString()
                    add(
                        Song(
                            id = "local:$rowId",
                            title = c.getString(title).orEmpty(),
                            artist = c.getString(artist).known(),
                            album = c.getString(album).known(),
                            durationMs = c.getLong(duration),
                            uri = uri,
                            artworkUri = uri,
                        ),
                    )
                }
            }
        } ?: emptyList()
    }
}

/** MediaStore spells a missing tag as "<unknown>"; treat that as no value. */
private fun String?.known(): String? =
    this?.takeUnless { it.isBlank() || it == MediaStore.UNKNOWN_STRING }
