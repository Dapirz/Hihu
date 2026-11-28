package app.hihu.views.shared

import androidx.compose.runtime.saveable.Saver
import app.hihu.music.Song

/**
 * Keeps a list of songs, or none, across the activity being made again (a rotation, a new font
 * size or language), so a dialog open for them stays open with them.
 */
val SongsSaver: Saver<List<Song>?, ArrayList<String>> = Saver(
    save = { songs ->
        songs?.flatMapTo(ArrayList()) { song ->
            listOf(song.id, song.title, song.artist ?: NONE, song.album ?: NONE, song.durationMs.toString(), song.uri, song.artworkUri ?: NONE)
        }
    },
    restore = { flat ->
        flat.chunked(FIELDS).map { (id, title, artist, album, duration, uri, artwork) ->
            Song(id, title, artist.orNull(), album.orNull(), duration.toLongOrNull() ?: 0, uri, artwork.orNull())
        }
    },
)

private operator fun <T> List<T>.component6(): T = this[5]
private operator fun <T> List<T>.component7(): T = this[6]

private fun String.orNull(): String? = takeUnless { it == NONE }

/** Stands for a missing value, which a saved list of strings cannot hold. */
private const val NONE = "\u0000"
private const val FIELDS = 7
