package com.daffa.hihu.state

import android.util.Log
import com.daffa.hihu.music.ExploreFeed
import com.daffa.hihu.music.MusicCharts
import com.daffa.hihu.music.MusicFeed
import com.daffa.hihu.music.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The explore feed as it loads: moods and shelves, or a failure. */
data class ExploreState(val feed: ExploreFeed? = null, val loading: Boolean = false, val failed: Boolean = false)

/**
 * What the Explore tab shows: YouTube Music's moods and new releases, and the trending chart for
 * the user's country. Both are fetched the first time the tab opens, not when the app starts.
 */
class Explore(
    private val charts: MusicCharts,
    private val feed: MusicFeed,
    private val scope: CoroutineScope,
) {
    private val pager = Pager<Song>(scope, Song::id, "explore")
    val trending: StateFlow<Paged<Song>> = pager.state

    private val _country = MutableStateFlow<String?>(null)

    /** The country the chart is for, once it has loaded. */
    val country: StateFlow<String?> = _country.asStateFlow()

    private val _page = MutableStateFlow(ExploreState())
    val page: StateFlow<ExploreState> = _page.asStateFlow()
    private var pageJob: Job? = null

    /** Loads the chart and the feed unless they already have. */
    fun open() {
        val now = pager.state.value
        if (!now.loaded && !now.loading) loadChart()
        if (_page.value.feed == null && !_page.value.loading) loadFeed()
    }

    /** Loads both again. */
    fun refresh() {
        loadChart()
        loadFeed()
    }

    fun loadMore() = pager.loadMore()

    private fun loadChart() = pager.load {
        val chart = charts.trending()
        _country.value = chart.country
        chart.songs
    }

    private fun loadFeed() {
        pageJob?.cancel()
        _page.value = _page.value.copy(loading = true, failed = false)
        pageJob = scope.launch {
            try {
                _page.value = ExploreState(feed.explore())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "explore: cannot load the explore page", e)
                _page.value = ExploreState(failed = true)
            }
        }
    }

    private companion object {
        const val TAG = "Hihu"
    }
}
