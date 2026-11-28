package app.hihu.state

import android.util.Log
import app.hihu.music.Mood
import app.hihu.music.MusicFeed
import app.hihu.music.Shelf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One mood's page as it loads. */
data class MoodState(val shelves: List<Shelf> = emptyList(), val loading: Boolean = true, val failed: Boolean = false)

/** An opened mood or genre: what it is and its shelves of playlists. */
class OpenMood(val mood: Mood, val state: StateFlow<MoodState>, val retry: () -> Unit)

/**
 * The mood and genre pages the user has opened, by reference, so going back shows one again
 * without reloading. Only the most recent few are kept; the descriptions are kept for good.
 */
class Moods(private val feed: MusicFeed, private val scope: CoroutineScope) {
    private val open = object : LinkedHashMap<String, OpenMood>(MAX_OPEN, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, OpenMood>) = size > MAX_OPEN
    }
    private val known = HashMap<String, Mood>()

    /** Opens [mood], starting to load its page unless it is already open. */
    fun open(mood: Mood): OpenMood {
        known[mood.ref] = mood
        return open.getOrPut(mood.ref) {
            val state = MutableStateFlow(MoodState())
            var job: Job? = null
            fun load() {
                // A retry replaces the request still on its way, so a late failure cannot undo it.
                job?.cancel()
                state.value = MoodState()
                job = scope.launch {
                    try {
                        state.value = MoodState(feed.mood(mood), loading = false)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "moods: cannot load a mood page", e)
                        state.value = MoodState(loading = false, failed = true)
                    }
                }
            }
            load()
            OpenMood(mood, state.asStateFlow(), ::load)
        }
    }

    /** The mood under [ref], reopened if it was dropped, or null if it was never opened. */
    operator fun get(ref: String): OpenMood? = open[ref] ?: known[ref]?.let(::open)

    private companion object {
        const val TAG = "Hihu"
        const val MAX_OPEN = 6
        const val LOAD_FACTOR = 0.75f
    }
}
