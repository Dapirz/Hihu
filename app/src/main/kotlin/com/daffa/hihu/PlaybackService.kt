package com.daffa.hihu

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
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

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val attributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSources()))
            .setAudioAttributes(attributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        // Hand decoding to the audio DSP when the device supports it, so the CPU can sleep while
        // the screen is off. ExoPlayer falls back to normal playback when it cannot.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setAudioOffloadPreferences(
                AudioOffloadPreferences.Builder()
                    .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
                    .build(),
            )
            .build()

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(open)
            .setCallback(Callback)
            .build()
    }

    /**
     * Local files and plain URLs open as they are. A provider URI such as `hihu-yt://<id>` is
     * resolved to its stream URL and headers on the loading thread, every time it is opened.
     */
    @OptIn(UnstableApi::class)
    private fun dataSources(): DataSource.Factory {
        val resolvers = (application as HihuApplication).resolvers.associateBy { it.scheme }
        val http = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
        return ResolvingDataSource.Factory(DefaultDataSource.Factory(this, http)) { spec ->
            val resolver = resolvers[spec.uri.scheme] ?: return@Factory spec
            val stream = resolver.resolve(spec.uri.toString())
            spec.withUri(Uri.parse(stream.url)).withAdditionalHeaders(stream.headers)
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
