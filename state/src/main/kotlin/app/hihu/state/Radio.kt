package app.hihu.state

import android.util.Log
import app.hihu.music.Collection
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
 * Songs in [hidden] never join the queue this way.
 *
 * With [autoplay] on, any other queue becomes the radio of its last song once that song plays, so
 * the music goes on instead of stopping. Not with shuffle on, where songs added at the end would
 * be shuffled in among the ones already played, nor with repeat on, which already goes on.
 */
class Radio(
    private val source: MusicRadio,
    private val playback: Playback,
    private val autoplay: StateFlow<Boolean>,
    private val hidden: StateFlow<Set<String>>,
    private val scope: CoroutineScope,
) {
    private val _seed = MutableStateFlow<String?>(null)

    /** The name of what the radio grew from, a song or a mix, while it is what plays; null otherwise. */
    val seed: StateFlow<String?> = _seed.asStateFlow()

    private var job: Job? = null
    private var next: (suspend () -> Page<Song>)? = null
    private var generation = -1
    private var artistJob: Job? = null

    /** Pages in a row that added nothing, and when a failed page may be asked for again. */
    private var emptyPages = 0
    private var quietUntil = 0L

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

    /** Whether [song]'s provider can make a radio from it; files on the phone cannot. */
    fun hasRadio(song: Song): Boolean = source.hasRadio(song)

    /** Plays [song] now, followed by its radio when its provider has one. */
    fun start(song: Song) {
        job?.cancel()
        fresh()
        // Shuffle off: the radio adds songs at the end and counts what is left in play order.
        playback.play(listOf(song), 0, shuffle = false)
        if (!source.hasRadio(song)) return
        generation = playback.generation
        _seed.value = song.title
        job = scope.launch { load { source.radio(song) } }
    }

    fun isMix(collection: Collection): Boolean = source.isMix(collection)

    /**
     * Plays [songs] of mix [collection] from [start], or all of them in a random order, and then
     * keeps the queue topped up with the rest of the mix, which does not end. Shuffle mode stays
     * off, so the songs added at the end play after these.
     */
    fun playMix(collection: Collection, all: List<Song>, start: Int, shuffle: Boolean) {
        // The song tapped plays even if hidden; the rest of the mix leaves hidden ones out.
        val tapped = all.getOrNull(start)
        val songs = all.filter { it == tapped || it.id !in hidden.value }
        if (songs.isEmpty()) return
        job?.cancel()
        fresh()
        when (shuffle) {
            true -> playback.play(songs.shuffled(), 0, shuffle = false)
            false -> playback.play(songs, songs.indexOf(tapped).coerceAtLeast(0), shuffle = false)
        }
        generation = playback.generation
        _seed.value = collection.title
        job = scope.launch { load { source.mix(collection) } }
    }

    fun hasArtistRadio(collection: Collection): Boolean = source.hasArtistRadio(collection)

    private val _startingArtist = MutableStateFlow(false)

    /** True while an artist's radio is being fetched, before it starts playing. */
    val startingArtist: StateFlow<Boolean> = _startingArtist.asStateFlow()

    /**
     * Plays the radio of artist [collection]: songs like theirs, kept topped up like any radio.
     * Nothing changes until its first page arrives, and if it cannot be loaded the queue playing
     * stays as it was.
     */
    fun startArtist(collection: Collection) {
        artistJob?.cancel()
        // The radio playing now goes on untouched until the artist's first page is here.
        artistJob = scope.launch {
            _startingArtist.value = true
            try {
                val page = source.artistRadio(collection)
                val songs = page.items.filter { it.id !in hidden.value }.distinctBy(Song::id)
                if (songs.isEmpty()) return@launch
                job?.cancel()
                fresh()
                playback.play(songs, 0, shuffle = false)
                generation = playback.generation
                next = page.next
                _seed.value = collection.title
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "radio: cannot load the artist's radio", e)
            } finally {
                _startingArtist.value = false
            }
        }
    }

    /** Whether [now] is the last song of a queue that would stop after it. */
    private fun endsHere(now: NowPlaying) = now.isPlaying && !now.hasNext && !now.shuffle && now.repeat == Repeat.Off

    /** Turns the queue playing into the radio of [song], its last one, without replacing it. */
    private fun carryOn(song: Song) {
        if (!source.hasRadio(song) || job?.isActive == true) return
        fresh()
        generation = playback.generation
        _seed.value = song.title
        job = scope.launch { load { source.radio(song) } }
    }

    private fun more() {
        val fetch = next ?: return
        if (job?.isActive == true || System.currentTimeMillis() < quietUntil) return
        job = scope.launch { load(fetch) }
    }

    /** Clears what a radio before this one left behind. */
    private fun fresh() {
        next = null
        emptyPages = 0
        quietUntil = 0
    }

    /**
     * Fetches a page and adds the songs that are not queued yet to the end of the queue. A song
     * counts as queued by its title and artist too, so a music video's song version does not
     * follow the video, which plays that very song already.
     */
    private suspend fun load(fetch: suspend () -> Page<Song>) {
        try {
            val page = fetch()
            if (!active()) return
            next = page.next
            val current = playback.queue.value
            val queued = current.mapTo(HashSet(), Song::id)
            val heard = current.mapTo(HashSet(), ::sameSong)
            val fresh = page.items.filter { it.id !in hidden.value && queued.add(it.id) && heard.add(sameSong(it)) }
            if (fresh.isNotEmpty()) playback.enqueue(fresh, resume = true)
            // A few pages in a row with nothing new and the radio has run dry; it stops asking.
            emptyPages = if (fresh.isEmpty()) emptyPages + 1 else 0
            if (emptyPages >= EMPTY_PAGES_MAX) next = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "radio: cannot load similar songs", e)
            // Not asked again on the next tick of the position, only after a pause.
            quietUntil = System.currentTimeMillis() + FAILED_PAUSE_MS
        }
    }

    private fun active() = _seed.value != null && playback.generation == generation

    /**
     * What makes two songs the same one to a listener: the title, without the marks of a video
     * like "(Official Video)", and the first artist, not the id. A video often credits one artist
     * where its song credits them all.
     */
    private fun sameSong(song: Song): String {
        val title = song.title.replace(BRACKETED) { match ->
            if (VIDEO_MARK.containsMatchIn(match.groupValues[1])) "" else match.value
        }
        // Letters and digits only, so "J. Balvin" and "J Balvin" are one artist.
        val artist = song.artist?.substringBefore(", ").orEmpty()
        return title.lowercase().replace(NOT_WORD, "") + "\u0000" + artist.lowercase().replace(NOT_WORD, "")
    }

    private companion object {
        const val TAG = "Hihu"
        /** Fetch more once this few songs are left after the one playing. */
        const val REFILL_AT = 3
        const val EMPTY_PAGES_MAX = 3
        const val FAILED_PAUSE_MS = 30_000L
        val NOT_WORD = Regex("[^\\p{L}\\p{N}]+")
        val BRACKETED = Regex("""\s*[(\[]([^)\]]*)[)\]]""")
        /** Parts of a title that do not change the song: a video's marks, and its guests, in any order. */
        val VIDEO_MARK = Regex("official|video|lyric|visuali[sz]er|audio|^feat\\b|^ft\\b|^with ", RegexOption.IGNORE_CASE)
    }
}
