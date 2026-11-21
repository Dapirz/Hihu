package app.hihu.state

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import app.hihu.music.Song
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** A snapshot of the player for the UI. [song] is null when nothing is loaded. */
data class NowPlaying(
    val song: Song? = null,
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: Repeat = Repeat.Off,
) {
    val songId: String? get() = song?.id
    val title: String get() = song?.title.orEmpty()
    val artist: String? get() = song?.artist
    val artworkUri: String? get() = song?.artworkUri
}

/** How the queue repeats: not at all, the whole queue, or the current song. */
enum class Repeat { Off, All, One }

/**
 * The one way the UI drives playback. It talks to the playback service through a
 * [MediaController], so views never touch the player, and the service keeps playing after the
 * activity goes away. Tell it when the UI shows and hides with [setVisible]. The binding is
 * kept while something plays even with the UI hidden, so the radio keeps filling the queue and
 * the history keeps every song; the service calls [connect] when playback starts without it.
 *
 * The queue is saved through [saved] whenever it changes, and put back when the app starts with
 * nothing loaded. [onSongStarted] hears about every song that begins, for the history.
 */
class Playback(
    private val context: Context,
    private val service: ComponentName,
    private val scope: CoroutineScope,
    private val saved: SavedQueue,
    private val onSongStarted: (Song) -> Unit,
) {
    private val _state = MutableStateFlow(NowPlaying())
    val state: StateFlow<NowPlaying> = _state.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())

    /** The play queue in its play order when shuffle is off; [NowPlaying.index] points into it. */
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private var pending: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var ticker: Job? = null
    private var saving: Job? = null
    private var restoring: Job? = null
    private var lastStarted: String? = null
    private var visible = false

    /** Counts the times the queue was replaced, so a radio can tell when it is no longer playing. */
    var generation: Int = 0
        private set

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) {
                readQueue()
                saveQueue()
            }
            sync()
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) || events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                savePosition()
            }
            if (events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED) || events.contains(Player.EVENT_REPEAT_MODE_CHANGED)) {
                saveModes()
            }
            // Released from outside the listener, since the controller is still calling it.
            if (!visible && !keepsPlaying(player)) context.mainExecutor.execute(::releaseIfIdle)
        }
    }

    private val connection = object : MediaController.Listener {
        override fun onDisconnected(controller: MediaController) {
            if (controller !== this@Playback.controller) return
            forget()
            if (visible) connect()
        }
    }

    /** Tells whether the UI is on screen. The service stays bound while it is and while something plays. */
    fun setVisible(shown: Boolean) {
        visible = shown
        when (shown) {
            true -> {
                connect()
                sync()
            }
            false -> {
                ticker?.cancel()
                ticker = null
                releaseIfIdle()
            }
        }
    }

    /** Binds to the playback service. Safe to call more than once. */
    fun connect() {
        if (pending != null) return
        val future = MediaController.Builder(context, SessionToken(context, service))
            .setListener(connection)
            .buildAsync()
        pending = future
        future.addListener({
            val built = runCatching { future.get() }
                .onFailure { Log.w(TAG, "playback: cannot connect to service", it) }
                .getOrNull()
            if (built == null) {
                // Cleared so the next call tries again.
                if (pending === future) pending = null
                return@addListener
            }
            if (pending !== future) {
                // Released while it was connecting.
                built.release()
                return@addListener
            }
            controller = built
            built.addListener(listener)
            readQueue()
            sync()
            if (built.mediaItemCount == 0) restore()
        }, context.mainExecutor)
    }

    /** Releases the binding once the UI is hidden and nothing is playing any more. */
    private fun releaseIfIdle() {
        if (visible) return
        val c = controller
        if (c != null && keepsPlaying(c)) return
        savePosition()
        restoring?.cancel()
        c?.removeListener(listener)
        pending?.let(MediaController::releaseFuture)
        forget()
    }

    private fun forget() {
        ticker?.cancel()
        ticker = null
        pending = null
        controller = null
    }

    /** Whether [player] is playing or about to, as opposed to paused, stopped or at the end. */
    private fun keepsPlaying(player: Player) = player.playWhenReady && player.playbackState != Player.STATE_ENDED

    /** Replaces the queue with [songs] and starts playing at [start], turning shuffle on or off if [shuffle] says. */
    fun play(songs: List<Song>, start: Int, shuffle: Boolean? = null) {
        val c = controller ?: return
        restoring?.cancel()
        shuffle?.let { c.shuffleModeEnabled = it }
        generation++
        c.setMediaItems(songs.map(::toMediaItem), start, 0)
        c.prepare()
        c.play()
    }

    /** Plays the whole of [songs], from the top or in a random order. */
    fun playAll(songs: List<Song>, shuffle: Boolean) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        c.shuffleModeEnabled = shuffle
        play(songs, if (shuffle) songs.indices.random() else 0)
    }

    /** Puts [songs] right after the current song, or starts them when nothing is loaded. */
    fun playNext(songs: List<Song>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(songs, 0)
        c.addMediaItems(c.currentMediaItemIndex + 1, songs.map(::toMediaItem))
    }

    /**
     * Adds [songs] to the end of the queue, or starts them when nothing is loaded. With [resume],
     * a queue that already played to its end goes on with the first of them.
     */
    fun enqueue(songs: List<Song>, resume: Boolean = false) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(songs, 0)
        val first = c.mediaItemCount
        val ended = c.playbackState == Player.STATE_ENDED
        c.addMediaItems(songs.map(::toMediaItem))
        if (resume && ended) {
            c.seekTo(first, 0)
            c.play()
        }
    }

    /** Jumps to the song at [index] in [queue] and plays it. */
    fun skipTo(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        c.seekTo(index, 0)
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        c.play()
    }

    fun removeAt(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    /**
     * The saved queue as the service needs it to start again after Hihu was closed, from a
     * headset's play button or the system's resume card; null when nothing was saved.
     */
    suspend fun resumable(): MediaSession.MediaItemsWithStartPosition? {
        val snapshot = runCatching { saved.load() }.getOrNull() ?: return null
        if (snapshot.songs.isEmpty()) return null
        return MediaSession.MediaItemsWithStartPosition(snapshot.songs.map(::toMediaItem), snapshot.index, snapshot.positionMs)
    }

    /** Takes every copy of the song [songId] out of the queue; if it is playing, the next one plays. */
    fun drop(songId: String) {
        val c = controller ?: return
        for (i in c.mediaItemCount - 1 downTo 0) {
            if (c.getMediaItemAt(i).mediaId == songId) c.removeMediaItem(i)
        }
    }

    /** Takes out the songs before the one playing. */
    fun removePlayed() {
        val c = controller ?: return
        val playing = c.currentMediaItemIndex
        if (playing > 0) c.removeMediaItems(0, playing)
    }

    /**
     * Takes out the songs after the one playing. This also ends a radio, which would otherwise
     * fill the queue straight back up.
     */
    fun clearUpcoming() {
        val c = controller ?: return
        val from = c.currentMediaItemIndex + 1
        if (from >= c.mediaItemCount) return
        generation++
        c.removeMediaItems(from, c.mediaItemCount)
    }

    fun move(from: Int, to: Int) {
        val c = controller ?: return
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount) c.moveMediaItem(from, to)
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    /** Steps the repeat mode off → all → one → off. */
    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) return c.pause()
        // A restored queue is not prepared until it is played, so opening the app costs no data.
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        c.play()
    }

    fun next() = controller?.seekToNextMediaItem() ?: Unit

    fun previous() = controller?.seekToPrevious() ?: Unit

    fun seekTo(positionMs: Long) = controller?.seekTo(positionMs) ?: Unit

    /** Copies the controller into [state], and keeps the position ticking while playing on screen. */
    private fun sync() {
        val c = controller ?: return
        val song = c.currentMediaItem?.let(::toSong)
        _state.value = NowPlaying(
            song = song,
            index = c.currentMediaItemIndex.takeIf { song != null } ?: -1,
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.takeIf { it > 0 } ?: 0,
            hasPrevious = song != null,
            hasNext = c.hasNextMediaItem(),
            shuffle = c.shuffleModeEnabled,
            repeat = repeatOf(c.repeatMode),
        )
        if (c.isPlaying && song != null && song.id != lastStarted) {
            lastStarted = song.id
            onSongStarted(song)
        }
        when (c.isPlaying && visible) {
            true -> if (ticker?.isActive != true) ticker = scope.launch { tick() }
            false -> {
                ticker?.cancel()
                ticker = null
            }
        }
    }

    private fun readQueue() {
        val c = controller ?: return
        _queue.value = (0 until c.mediaItemCount).map { toSong(c.getMediaItemAt(it)) }
    }

    /** Saves the queue a moment after it stops changing, so a burst of edits is one write. */
    private fun saveQueue() {
        val c = controller ?: return
        val snapshot = QueueSnapshot(_queue.value, c.currentMediaItemIndex, c.currentPosition, c.shuffleModeEnabled, repeatOf(c.repeatMode))
        saving?.cancel()
        saving = scope.launch {
            delay(SAVE_DELAY_MS)
            try {
                saved.save(snapshot)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "playback: cannot save the queue", e)
            }
        }
    }

    private fun savePosition() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return
        val index = c.currentMediaItemIndex
        val position = c.currentPosition
        scope.launch {
            runCatching { saved.savePosition(index, position) }.onFailure { Log.w(TAG, "playback: cannot save the position", it) }
        }
    }

    private fun saveModes() {
        val c = controller ?: return
        val shuffle = c.shuffleModeEnabled
        val repeat = repeatOf(c.repeatMode)
        scope.launch {
            runCatching { saved.saveModes(shuffle, repeat) }.onFailure { Log.w(TAG, "playback: cannot save shuffle and repeat", it) }
        }
    }

    /**
     * Loads the saved queue into the player, paused where it was left, with its shuffle and repeat.
     * It is prepared only when played, so nothing is fetched until then.
     */
    private fun restore() {
        restoring = scope.launch {
            val snapshot = runCatching { saved.load() }.getOrNull() ?: return@launch
            val c = controller ?: return@launch
            if (c.mediaItemCount != 0) return@launch
            c.shuffleModeEnabled = snapshot.shuffle
            c.repeatMode = when (snapshot.repeat) {
                Repeat.Off -> Player.REPEAT_MODE_OFF
                Repeat.All -> Player.REPEAT_MODE_ALL
                Repeat.One -> Player.REPEAT_MODE_ONE
            }
            c.setMediaItems(snapshot.songs.map(::toMediaItem), snapshot.index, snapshot.positionMs)
        }
    }

    private suspend fun tick() {
        while (scope.isActive) {
            val c = controller ?: return
            _state.value = _state.value.copy(positionMs = c.currentPosition.coerceAtLeast(0))
            delay(TICK_MS)
        }
    }

    private companion object {
        const val TAG = "Hihu"
        const val TICK_MS = 500L
        const val SAVE_DELAY_MS = 500L
    }
}

/**
 * Where the UI loads a cover from. It is kept out of MediaMetadata.artworkUri because a local
 * song's cover URI is the audio file itself, which the notification cannot decode as an image;
 * the notification uses the artwork embedded in the file instead.
 */
private fun repeatOf(mode: Int): Repeat = when (mode) {
    Player.REPEAT_MODE_ALL -> Repeat.All
    Player.REPEAT_MODE_ONE -> Repeat.One
    else -> Repeat.Off
}

private const val EXTRA_ARTWORK = "hihu.artwork"
private const val EXTRA_DURATION = "hihu.duration"

/**
 * The URI travels in requestMetadata, because a controller strips a MediaItem's local
 * configuration on its way to the session. The service turns it back into a playable item.
 */
private fun toMediaItem(song: Song): MediaItem {
    val uri = Uri.parse(song.uri)
    return MediaItem.Builder()
        .setMediaId(song.id)
        .setUri(uri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .setExtras(Bundle().apply {
                    putString(EXTRA_ARTWORK, song.artworkUri)
                    putLong(EXTRA_DURATION, song.durationMs)
                })
                // An image URL can go to the notification as is; a local cover cannot (see above).
                .setArtworkUri(song.artworkUri?.takeIf { it.startsWith("http") }?.let(Uri::parse))
                .build(),
        )
        .build()
}

/** The [Song] a queue item was made from, read back from what [toMediaItem] put in it. */
private fun toSong(item: MediaItem): Song {
    val meta = item.mediaMetadata
    return Song(
        id = item.mediaId,
        title = meta.title?.toString().orEmpty(),
        artist = meta.artist?.toString(),
        album = meta.albumTitle?.toString(),
        durationMs = meta.extras?.getLong(EXTRA_DURATION) ?: 0,
        uri = item.requestMetadata.mediaUri?.toString() ?: item.localConfiguration?.uri?.toString().orEmpty(),
        artworkUri = meta.extras?.getString(EXTRA_ARTWORK),
    )
}
