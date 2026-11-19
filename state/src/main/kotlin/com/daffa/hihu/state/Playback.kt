package com.daffa.hihu.state

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.daffa.hihu.music.Song
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
 * activity goes away. Call [connect] when the UI starts and [disconnect] when it stops.
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
        }
    }

    /** Binds to the playback service. Safe to call more than once. */
    fun connect() {
        if (pending != null) return
        val future = MediaController.Builder(context, SessionToken(context, service)).buildAsync()
        pending = future
        future.addListener({
            val built = runCatching { future.get() }
                .onFailure { Log.w(TAG, "playback: cannot connect to service", it) }
                .getOrNull() ?: return@addListener
            controller = built
            built.addListener(listener)
            readQueue()
            sync()
            if (built.mediaItemCount == 0) restore()
        }, context.mainExecutor)
    }

    /** Releases the binding. Playback keeps going in the service if it was playing. */
    fun disconnect() {
        savePosition()
        ticker?.cancel()
        ticker = null
        restoring?.cancel()
        controller?.removeListener(listener)
        pending?.let(MediaController::releaseFuture)
        pending = null
        controller = null
    }

    /** Replaces the queue with [songs] and starts playing at [start]. */
    fun play(songs: List<Song>, start: Int) {
        val c = controller ?: return
        restoring?.cancel()
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

    /** Adds [songs] to the end of the queue, or starts them when nothing is loaded. */
    fun enqueue(songs: List<Song>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(songs, 0)
        c.addMediaItems(songs.map(::toMediaItem))
    }

    /** Jumps to the song at [index] in [queue] and plays it. */
    fun skipTo(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        c.seekTo(index, 0)
        c.play()
    }

    fun removeAt(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
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
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() = controller?.seekToNextMediaItem() ?: Unit

    fun previous() = controller?.seekToPrevious() ?: Unit

    fun seekTo(positionMs: Long) = controller?.seekTo(positionMs) ?: Unit

    /** Copies the controller into [state], and keeps the position ticking while playing. */
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
            repeat = when (c.repeatMode) {
                Player.REPEAT_MODE_ALL -> Repeat.All
                Player.REPEAT_MODE_ONE -> Repeat.One
                else -> Repeat.Off
            },
        )
        if (c.isPlaying && song != null && song.id != lastStarted) {
            lastStarted = song.id
            onSongStarted(song)
        }
        when (c.isPlaying) {
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
        val snapshot = QueueSnapshot(_queue.value, c.currentMediaItemIndex, c.currentPosition)
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

    /** Loads the saved queue into the player, paused where it was left. */
    private fun restore() {
        restoring = scope.launch {
            val snapshot = runCatching { saved.load() }.getOrNull() ?: return@launch
            val c = controller ?: return@launch
            if (c.mediaItemCount != 0) return@launch
            c.setMediaItems(snapshot.songs.map(::toMediaItem), snapshot.index, snapshot.positionMs)
            c.prepare()
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
