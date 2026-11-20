package app.hihu

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import app.hihu.music.SongLoudness
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import kotlin.math.pow

/**
 * Evens out the volume between songs: a song louder than the provider's target is turned down
 * by the difference, through the player's own volume; quieter songs play as they are, since
 * turning them up could clip. Each song's loudness is asked for once and kept on the device, so
 * a song played again, or downloaded and played offline, needs no request.
 */
class Normalizer(
    context: Context,
    private val source: SongLoudness,
    private val enabled: StateFlow<Boolean>,
    private val scope: CoroutineScope,
) {
    private val known = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // Songs being fetched, or that came back without a figure, in this run; not asked again.
    private val asked = Collections.synchronizedSet(HashSet<String>())

    private var player: Player? = null
    private var watching: Job? = null

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = refresh()

        override fun onTimelineChanged(timeline: Timeline, reason: Int) = refresh()
    }

    /** Starts setting [player]'s volume for each song it plays; [detach] stops it. */
    fun attach(player: Player) {
        this.player = player
        player.addListener(listener)
        watching = scope.launch { enabled.collect { refresh() } }
    }

    fun detach() {
        watching?.cancel()
        player?.removeListener(listener)
        player = null
    }

    /** Asks for [uri]'s loudness ahead of time, as for a song being downloaded, unless it is known. */
    fun prefetch(uri: String) {
        if (known.contains(uri) || !asked.add(uri)) return
        scope.launch {
            val db = withContext(Dispatchers.IO) {
                runCatching { source.loudnessDb(uri) }
                    .onFailure { Log.w(TAG, "volume: cannot read a song's loudness", it) }
                    .getOrNull()
            }
            if (db != null) {
                known.edit().putFloat(uri, db.toFloat()).apply()
                asked.remove(uri)
            }
            if (uriOf(player?.currentMediaItem) == uri) refresh()
        }
    }

    /** Sets the volume for the song playing and asks ahead for it and the next one. */
    private fun refresh() {
        val player = player ?: return
        val current = uriOf(player.currentMediaItem)
        val gain = gain(current)
        if (gain != player.volume) Log.i(TAG, "volume: player volume ${"%.2f".format(gain)} for the song playing")
        player.volume = gain
        current?.let(::prefetch)
        val next = player.nextMediaItemIndex
        if (next != androidx.media3.common.C.INDEX_UNSET) uriOf(player.getMediaItemAt(next))?.let(::prefetch)
    }

    /**
     * The player volume for [uri]: 1 when off or not too loud, lower for a loud song. While a
     * YouTube song's loudness is still on its way the volume stays where it was, since most songs
     * are mastered about as loud as the one before and jumping to full would blast its start.
     */
    private fun gain(uri: String?): Float {
        if (!enabled.value || uri == null || !source.knows(uri)) return FULL
        if (!known.contains(uri)) return player?.volume ?: FULL
        val db = known.getFloat(uri, 0f)
        if (db <= 0f) return FULL
        return 10.0.pow(-db / DB_PER_TENFOLD).toFloat().coerceIn(MIN_GAIN, FULL)
    }

    private fun uriOf(item: MediaItem?): String? =
        (item?.localConfiguration?.uri ?: item?.requestMetadata?.mediaUri)?.let(Uri::toString)

    private companion object {
        const val TAG = "Hihu"
        const val FILE = "loudness"
        const val FULL = 1f
        // A very loud master is still turned down by no more than 20 dB.
        const val MIN_GAIN = 0.1f
        // Amplitude changes tenfold every 20 dB.
        const val DB_PER_TENFOLD = 20.0
    }
}
