package app.hihu.state

import android.util.Log
import app.hihu.music.ContentUnavailable
import app.hihu.music.MusicSearch
import app.hihu.music.Page
import app.hihu.music.PlaylistLinks
import app.hihu.music.SearchKind
import app.hihu.music.SearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What was last searched for, and the completions for what is being typed now. */
data class SearchQuery(
    val text: String = "",
    val kind: SearchKind = SearchKind.Songs,
    val suggestions: List<String> = emptyList(),
)

/**
 * Searches a [MusicSearch] and keeps the results for the whole app. [query] holds what was
 * submitted and the suggestions; [results] the pages of hits, appended by [loadMore]. A pasted
 * song link, such as `youtu.be/…`, comes back as that one song instead of a search for the text.
 */
class Search(
    private val source: MusicSearch,
    /** Reads pasted song links. */
    private val links: PlaylistLinks,
    private val scope: CoroutineScope,
    /** Every submitted query is noted here, for the list shown while the field is empty. */
    val history: SearchHistory,
) {
    private val _query = MutableStateFlow(SearchQuery())
    val query: StateFlow<SearchQuery> = _query.asStateFlow()

    private val pager = Pager<SearchResult>(scope, SearchResult::key, "search")
    val results: StateFlow<Paged<SearchResult>> = pager.state

    private var suggesting: Job? = null

    /** Searches for [text] in the current kind. Blank text clears the results. */
    fun submit(text: String) {
        val trimmed = text.trim()
        suggesting?.cancel()
        _query.value = _query.value.copy(text = trimmed, suggestions = emptyList())
        history.record(trimmed)
        run()
    }

    /** Switches what is searched for, and runs the last query again in the new kind. */
    fun select(kind: SearchKind) {
        if (_query.value.kind == kind) return
        _query.value = _query.value.copy(kind = kind)
        run()
    }

    fun loadMore() = pager.loadMore()

    fun retry() = pager.retry()

    /**
     * Fetches completions for [text] once typing pauses, replacing any request still waiting.
     * Blank text clears them.
     */
    fun suggest(text: String) {
        suggesting?.cancel()
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed == _query.value.text) {
            _query.value = _query.value.copy(suggestions = emptyList())
            return
        }
        suggesting = scope.launch {
            delay(SUGGEST_DELAY_MS)
            try {
                val found = source.suggestions(trimmed).take(MAX_SUGGESTIONS)
                _query.value = _query.value.copy(suggestions = found)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "search: cannot fetch suggestions", e)
                // The old ones were for other text.
                _query.value = _query.value.copy(suggestions = emptyList())
            }
        }
    }

    private fun run() {
        val (text, kind) = _query.value
        when (text.isEmpty()) {
            true -> pager.clear()
            false -> pager.load { linked(text) ?: source.search(text, kind) }
        }
    }

    /**
     * The song a link in [text] points at, as the only result, or null when [text] holds no song
     * link and should be searched for. A link to a song that is private or gone finds nothing.
     */
    private suspend fun linked(text: String): Page<SearchResult>? {
        val link = linkIn(text) ?: return null
        val song = try {
            links.song(link)
        } catch (e: ContentUnavailable) {
            Log.w(TAG, "search: the linked song is unavailable", e)
            return Page(emptyList(), null)
        } ?: return null
        return Page(listOf(SearchResult.Track(song)), null)
    }

    private companion object {
        const val TAG = "Hihu"
        const val SUGGEST_DELAY_MS = 250L
        const val MAX_SUGGESTIONS = 8
    }
}
