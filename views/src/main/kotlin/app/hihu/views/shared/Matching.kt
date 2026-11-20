package app.hihu.views.shared

import app.hihu.music.Song
import java.text.Normalizer

/**
 * The items whose [text] contains [query], ignoring case and accents, so "beyonce" finds
 * "Beyoncé". A blank query keeps them all.
 */
fun <T> List<T>.matching(query: String, text: (T) -> String): List<T> {
    val needle = fold(query.trim())
    if (needle.isEmpty()) return this
    return filter { fold(text(it)).contains(needle) }
}

/** The songs whose title, artist or album contains [query]; see [matching]. */
fun List<Song>.matchingSongs(query: String): List<Song> =
    matching(query) { song -> listOfNotNull(song.title, song.artist, song.album).joinToString(" ") }

private fun fold(text: String): String = MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase()

private val MARKS = Regex("\\p{Mn}+")
