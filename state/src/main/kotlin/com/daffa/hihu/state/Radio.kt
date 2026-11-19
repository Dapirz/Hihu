package com.daffa.hihu.state

import android.util.Log
import com.daffa.hihu.music.MusicRadio
import com.daffa.hihu.music.Page
import com.daffa.hihu.music.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Plays a song and keeps the queue topped up with songs like it, the way YouTube Music does after
 * a search. More are fetched when only a few are left. Playing anything else ends the radio.
 */
class Radio(
    private val source: MusicRadio,
    private val playback: Playback,
    private val scope: CoroutineScope,
) {
    private val _seed = MutableStateFlow<Song?>(null)

    /** The song the radio grew from, while the radio is what is playing; null otherwise. */
    val seed: StateFlow<Song?> = _seed.asStateFlow()

    private var job: Job? = null
    private var next: (suspend () -> Page<Song>)? = null
    private var generation = -1

    init {
        scope.launch {
            combine(playback.state, playback.queue) { now, queue -> now.index to queue.size }.collect { (index, size) ->
                if (playback.generation != generation) _seed.value = null
                if (active() && index >= 0 && size - index <= REFILL_AT) more()
            }
        }
    }

    /** Plays [song] now, followed by its radio when its provider has one. */
    fun start(song: Song) {
        job?.cancel()
        next = null
        playback.play(listOf(song), 0)
        if (!source.hasRadio(song)) return
        generation = playback.generation
        _seed.value = song
        job = scope.launch { load { source.radio(song) } }
    }

    private fun more() {
        val fetch = next ?: return
        if (job?.isActive == true) return
        job = scope.launch { load(fetch) }
    }

    /** Fetches a page and adds the songs that are not queued yet to the end of the queue. */
    private suspend fun load(fetch: suspend () -> Page<Song>) {
        try {
            val page = fetch()
            if (!active()) return
            next = page.next
            val queued = playback.queue.value.mapTo(HashSet(), Song::id)
            val fresh = page.items.filter { queued.add(it.id) }
            if (fresh.isNotEmpty()) playback.enqueue(fresh)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "radio: cannot load similar songs", e)
        }
    }

    private fun active() = _seed.value != null && playback.generation == generation

    private companion object {
        const val TAG = "Hihu"
        /** Fetch more once this few songs are left after the one playing. */
        const val REFILL_AT = 3
    }
}
