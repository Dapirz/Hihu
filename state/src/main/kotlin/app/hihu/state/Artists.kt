package app.hihu.state

import android.util.Log
import app.hihu.music.Collection
import app.hihu.music.CollectionKind
import app.hihu.music.MusicSearch
import app.hihu.music.SearchKind
import app.hihu.music.SearchResult
import app.hihu.music.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Finds the artist page behind a name credited on a song, so tapping the name opens it. A song
 * carries only the names, so the page is the artist search's hit with that very name.
 */
class Artists(private val search: MusicSearch, private val scope: CoroutineScope) {
    private var finding: Job? = null

    /** The names credited on [song], one per artist, as in "A, B & C"; empty for a song with none. */
    fun names(song: Song): List<String> = song.artist.orEmpty()
        .split(CREDIT_BREAK)
        .map { it.trim().removeSuffix(" - Topic").removeSuffix("VEVO").trim() }
        .filter { it.isNotEmpty() }
        .distinct()

    /**
     * Looks up the page of the artist [name]: [onFound] gets it, or [onMissing] runs when no
     * artist goes by exactly that name or the search failed. A newer call replaces this one.
     */
    fun find(name: String, onFound: (Collection) -> Unit, onMissing: () -> Unit) {
        finding?.cancel()
        finding = scope.launch {
            val wanted = plain(name)
            val artist = try {
                search.search(name, SearchKind.Artists).items
                    .mapNotNull { (it as? SearchResult.Group)?.collection }
                    .firstOrNull { it.kind == CollectionKind.Artist && plain(it.title) == wanted }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "artists: cannot search for an artist", e)
                null
            }
            when (artist) {
                null -> onMissing()
                else -> onFound(artist)
            }
        }
    }

    /** Lowercase letters and digits only, so case and punctuation never tell two names apart. */
    private fun plain(name: String): String = name.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    private companion object {
        const val TAG = "Hihu"

        /** What separates two names in a credit line. */
        val CREDIT_BREAK = Regex("""\s*(?:,|&|\b(?:feat|ft)\b\.?|\bfeaturing\b)\s*""", RegexOption.IGNORE_CASE)
    }
}
