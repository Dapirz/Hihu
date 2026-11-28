package app.hihu.state

import android.util.Log
import app.hihu.music.Page
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A list that arrives a page at a time. [loading] and [failed] are about the first page;
 * [loadingMore] and [moreFailed] about the pages after it.
 */
data class Paged<T>(
    val items: List<T> = emptyList(),
    val loading: Boolean = false,
    val failed: Boolean = false,
    val loaded: Boolean = false,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val moreFailed: Boolean = false,
    /** The size of the whole list, when the provider said so on the first page. */
    val total: Long? = null,
)

/**
 * Loads a [Page] and the pages after it into one list, dropping items whose [key] is already in
 * it, since providers repeat items across pages and duplicate keys break lazy lists.
 */
class Pager<T>(
    private val scope: CoroutineScope,
    private val key: (T) -> String,
    private val subsystem: String,
) {
    private val _state = MutableStateFlow(Paged<T>())
    val state: StateFlow<Paged<T>> = _state.asStateFlow()

    private var job: Job? = null
    private var first: (suspend () -> Page<T>)? = null
    private var next: (suspend () -> Page<T>)? = null

    /** Starts over from [fetch], cancelling whatever was loading. */
    fun load(fetch: suspend () -> Page<T>) {
        job?.cancel()
        first = fetch
        next = null
        _state.value = Paged(loading = true)
        job = scope.launch {
            try {
                val page = fetch()
                next = page.next
                _state.value = Paged(
                    items = page.items.distinctBy(key),
                    loaded = true,
                    hasMore = page.next != null,
                    total = page.total,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "$subsystem: cannot load the first page", e)
                _state.value = Paged(failed = true, loaded = true)
            }
        }
    }

    /** Appends the next page, unless one is already on its way or there is none. */
    fun loadMore() {
        val fetch = next ?: return
        if (job?.isActive == true) return
        _state.update { it.copy(loadingMore = true, moreFailed = false) }
        job = scope.launch {
            try {
                val page = fetch()
                next = page.next
                _state.update { current ->
                    val known = current.items.mapTo(HashSet(), key)
                    val added = page.items.filter { known.add(key(it)) }
                    // A page with nothing new would be asked for again at once; the list ends there.
                    if (added.isEmpty()) next = null
                    current.copy(
                        items = current.items + added,
                        hasMore = next != null,
                        loadingMore = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "$subsystem: cannot load more", e)
                _state.update { it.copy(loadingMore = false, moreFailed = true) }
            }
        }
    }

    /** Loads the first page again after it failed. */
    fun retry() {
        first?.let(::load)
    }

    /** Empties the list and stops loading. */
    fun clear() {
        job?.cancel()
        first = null
        next = null
        _state.value = Paged()
    }

    private companion object {
        const val TAG = "Hihu"
    }
}
