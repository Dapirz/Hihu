package app.hihu.state

import android.util.Log
import app.hihu.music.MusicRadio
import app.hihu.music.Page
import app.hihu.music.Song
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
 *
 * With [autoplay] on, any other queue becomes the radio of its last song once that song plays, so
 * the music goes on instead of stopping. Not with shuffle on, where songs added at the end would
 * be shuffled in among the ones already played, nor with repeat on, which already goes on.
 */
class Radio(
    private val source: MusicRadio,
    private val playback: Playback,
    private val autoplay: StateFlow<Boolean>,
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
            combine(playback.state, playback.queue) { now, queue -> now to queue.size }.collect { (now, size) ->
                if (playback.generation != generation) _seed.value = null
                val song = now.song
                when {
                    active() -> if (now.index >= 0 && size - now.index <= REFILL_AT) more()
                    song != null && autoplay.value && endsHere(now) -> carryOn(song)
                }
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

    /** Whether [now] is the last song of a queue that would stop after it. */
    private fun endsHere(now: NowPlaying) = now.isPlaying && !now.hasNext && !now.shuffle && now.repeat == Repeat.Off

    /** Turns the queue playing into the radio of [song], its last one, without replacing it. */
    private fun carryOn(song: Song) {
        if (!source.hasRadio(song) || job?.isActive == true) return
        next = null
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
            if (fresh.isNotEmpty()) playback.enqueue(fresh, resume = true)
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
