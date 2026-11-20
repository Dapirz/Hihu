package app.hihu

import android.app.PendingIntent
import android.content.Intent
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
        session = MediaSession.Builder(this, player)
            .setSessionActivity(open)
            .setCallback(Callback)
            .build()
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
     * from a MediaItem, so state.Playback carries it in requestMetadata instead.
     */
    private object Callback : MediaSession.Callback {
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
}
