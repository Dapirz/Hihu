package app.hihu

import android.app.PendingIntent
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.PlaybackException
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.launch
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Owns the one ExoPlayer and its MediaSession, which gives the notification, lockscreen and
 * headset controls. The UI reaches it only through state.Playback's MediaController.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val normalizer get() = (application as HihuApplication).normalizer

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val attributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val player = ExoPlayer.Builder(this)
            // Downloaded songs come from the cache with no network; the rest stream.
            .setMediaSourceFactory(DefaultMediaSourceFactory((application as HihuApplication).offline.playerSource))
            .setAudioAttributes(attributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        // Hand decoding to the audio DSP when the device supports it, so the CPU can sleep while
        // the screen is off. ExoPlayer falls back to normal playback when it cannot. Only where
        // the DSP also handles one song running straight into the next, so no device that lacks
        // it changes songs in the middle of the audio it was handed.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setAudioOffloadPreferences(
                AudioOffloadPreferences.Builder()
                    .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
                    .setIsGaplessSupportRequired(true)
                    .build(),
            )
            .build()

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        normalizer.attach(player)
        // Playing started from the notification, a headset or Bluetooth while the app was closed:
        // state.Playback binds again, so the radio and the history follow it.
        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (playWhenReady) (application as HihuApplication).state.playback.connect()
            }

            // A streamed song also holds a Wi-Fi lock, so Wi-Fi does not doze off under it with the
            // screen off; a downloaded or local song needs no network and leaves Wi-Fi alone.
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val streams = mediaItem != null && (application as HihuApplication).offline.streams(mediaItem)
                player.setWakeMode(
                    when (streams) {
                        true -> C.WAKE_MODE_NETWORK
                        false -> C.WAKE_MODE_LOCAL
                    },
                )
            }
        })
        player.addListener(retrying)
        connectivity.registerDefaultNetworkCallback(network)
        session = MediaSession.Builder(this, player)
            .setSessionActivity(open)
            .setCallback(Callback(application as HihuApplication))
            .build()
        audio.registerAudioDeviceCallback(headphones, null)
    }

    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private val connectivity by lazy { getSystemService(ConnectivityManager::class.java) }
    private val main = Handler(Looper.getMainLooper())

    /** How many times the song [retriedId] has been tried again since it last failed for good. */
    private var retries = 0
    private var retriedId: String? = null

    /** True while a failed stream waits for the phone to be online again. */
    private var waitingForNetwork = false

    /**
     * Tries a stream again when it fails on the network: a few times with a growing wait, or once
     * the phone is online again if it is offline. A song that keeps failing gives way to the next,
     * so the music does not stop over one bad song.
     */
    private val retrying = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            val player = session?.player ?: return
            if (error.errorCode !in NETWORK_ERRORS) return
            if (!online()) {
                Log.w(TAG, "playback: offline, waiting for the network to try again")
                waitingForNetwork = true
                return
            }
            val id = player.currentMediaItem?.mediaId
            if (id != retriedId) {
                retriedId = id
                retries = 0
            }
            if (retries >= MAX_RETRIES) {
                Log.w(TAG, "playback: a song kept failing (${error.errorCodeName}), playing the next")
                retries = 0
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                }
                return
            }
            val wait = FIRST_RETRY_MS shl retries
            retries++
            Log.w(TAG, "playback: stream failed (${error.errorCodeName}), trying again in $wait ms")
            main.postDelayed({ retry(player) }, wait)
        }
    }

    private val network = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            main.post {
                if (!waitingForNetwork) return@post
                waitingForNetwork = false
                session?.player?.let(::retry)
            }
        }
    }

    /** Opens the failed song again where it stopped, if it is still the one that failed. */
    private fun retry(player: Player) {
        if (player.playerError == null) return
        player.prepare()
    }

    private fun online(): Boolean = connectivity.activeNetwork != null

    /**
     * Plays on when headphones connect, if the user turned that on and a song is paused. Only
     * while this service runs: Android does not let a closed app start playing on its own.
     */
    private val headphones = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            val app = application as HihuApplication
            if (!app.state.preferences.resumeOnConnect.value) return
            if (addedDevices.none { it.isSink && it.type in HEADPHONES }) return
            val player = session?.player ?: return
            if (player.playWhenReady || player.mediaItemCount == 0) return
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Stops the service when the app is swiped away while nothing is playing. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        runCatching { connectivity.unregisterNetworkCallback(network) }
        audio.unregisterAudioDeviceCallback(headphones)
        normalizer.detach()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /**
     * Turns the items a controller sends back into playable ones. A controller strips the URI
     * from a MediaItem, so state.Playback carries it in requestMetadata instead. It also hands
     * back the saved queue when playing is resumed after Hihu was closed.
     */
    private class Callback(private val app: HihuApplication) : MediaSession.Callback {
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            app.scope.launch {
                when (val saved = app.state.playback.resumable()) {
                    null -> future.setException(UnsupportedOperationException("playback: nothing to resume"))
                    else -> future.set(saved)
                }
            }
            return future
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(
                mediaItems.mapTo(mutableListOf()) { item ->
                    item.requestMetadata.mediaUri?.let { item.buildUpon().setUri(it).build() } ?: item
                },
            )
    }

    private companion object {
        const val TAG = "Hihu"
        const val MAX_RETRIES = 3
        const val FIRST_RETRY_MS = 2_000L

        /** Failures the network may get over: a dropped or slow connection, or a refused URL. */
        val NETWORK_ERRORS = setOf(
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        )

        /** The outputs that count as headphones: Bluetooth, wired, and USB headsets or dongles. */
        val HEADPHONES = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            // A USB-C dongle for wired earphones often reports itself as a plain USB device.
            AudioDeviceInfo.TYPE_USB_DEVICE,
        )
    }
}
