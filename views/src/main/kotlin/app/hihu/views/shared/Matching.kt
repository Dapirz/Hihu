package app.hihu.views.shared

import app.hihu.music.Song
import app.hihu.state.LibrarySort
import java.text.Collator
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

/**
 * The songs in [sort]'s order. [LibrarySort.Added] keeps the list's own order, newest first;
 * titles and artists compare as the phone's language does, ignoring case and accents, and songs
 * with no artist go last.
 */
fun List<Song>.sortedFor(sort: LibrarySort): List<Song> {
    val words = Collator.getInstance().apply { strength = Collator.PRIMARY }
    return when (sort) {
        LibrarySort.Added, LibrarySort.Count -> this
        LibrarySort.Title -> sortedWith(compareBy(words) { it.title })
        LibrarySort.Artist -> sortedWith(compareBy<Song, String?>(nullsLast(words)) { it.artist }.thenBy(words) { it.title })
    }
}

private fun fold(text: String): String = MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase()

private val MARKS = Regex("\\p{Mn}+")
