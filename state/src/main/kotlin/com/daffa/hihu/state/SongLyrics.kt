package com.daffa.hihu.state

import android.util.Log
import android.util.LruCache
import com.daffa.hihu.music.Lyrics
import com.daffa.hihu.music.LyricsProvider
import com.daffa.hihu.music.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The lyrics of the song playing now, and how fetching them is going. */
data class LyricsState(
    val songId: String? = null,
    val lyrics: Lyrics? = null,
    val loading: Boolean = false,
    val missing: Boolean = false,
    val failed: Boolean = false,
)

/**
 * Fetches the lyrics of the song playing, but only while the lyrics are on screen ([setVisible]),
 * so nothing about songs whose lyrics are never opened leaves the phone. Each song is fetched
 * once; a song played again comes from memory, and so does finding none.
 */
class SongLyrics(
    private val source: LyricsProvider,
    private val playback: Playback,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(LyricsState())
    val state: StateFlow<LyricsState> = _state.asStateFlow()

    private val cache = LruCache<String, Found>(MAX_CACHED)
    private var job: Job? = null

    private class Found(val lyrics: Lyrics?)

    private val visible = MutableStateFlow(false)

    init {
        scope.launch {
            val song = playback.state.map { it.song }.distinctUntilChanged { a, b -> a?.id == b?.id }
            combine(song, visible) { s, shown -> s to shown }.collect { (s, shown) ->
                if (shown && s?.id != _state.value.songId) load(s)
            }
        }
    }

    /** Tells whether the lyrics are on screen; they are only fetched while they are. */
    fun setVisible(shown: Boolean) {
        visible.value = shown
    }

    /** Tries again for the song playing now, after fetching failed. */
    fun retry() {
        val song = playback.state.value.song ?: return
        cache.remove(song.id)
        load(song)
    }

    private fun load(song: Song?) {
        job?.cancel()
        if (song == null) {
            _state.value = LyricsState()
            return
        }
        cache.get(song.id)?.let {
            _state.value = LyricsState(song.id, it.lyrics, missing = it.lyrics == null)
            return
        }
        _state.value = LyricsState(song.id, loading = true)
        job = scope.launch {
            try {
                val lyrics = source.lyrics(song)
                cache.put(song.id, Found(lyrics))
                _state.value = LyricsState(song.id, lyrics, missing = lyrics == null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "lyrics: cannot fetch lyrics", e)
                _state.value = LyricsState(song.id, failed = true)
            }
        }
    }

    private companion object {
        const val TAG = "Hihu"
        const val MAX_CACHED = 50
    }
}
