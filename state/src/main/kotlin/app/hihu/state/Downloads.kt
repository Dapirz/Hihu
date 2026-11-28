package app.hihu.state

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import app.hihu.music.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Where one song's download is. [percent] runs from 0 to 100 while downloading. */
data class DownloadProgress(val status: DownloadStatus, val percent: Float = 0f)

enum class DownloadStatus { Queued, Downloading, Done, Failed }

/**
 * What actually fetches and stores songs. The app module implements it with Media3's
 * DownloadManager, so `state` never sees ExoPlayer. Keys are song ids.
 */
interface DownloadEngine {
    /** Every download the engine knows, by song id. */
    val progress: StateFlow<Map<String, DownloadProgress>>

    /** True once [progress] holds what the engine had stored, not just an empty start. */
    val loaded: StateFlow<Boolean>

    /** Whether [song] can be downloaded at all; files already on the phone cannot. */
    fun supports(song: Song): Boolean

    fun start(song: Song)

    fun remove(songId: String)

    /** Space the downloads take on the phone, in bytes. Reads the disk; call off the main thread. */
    fun bytesUsed(): Long
}

/**
 * The songs the user has downloaded, newest first, kept in the database alongside the engine's
 * own records so the list has titles and covers even with no network.
 */
class Downloads(private val engine: DownloadEngine, private val store: Store, private val scope: CoroutineScope) {
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    /** Progress by song id, for badges and the list. */
    val progress: StateFlow<Map<String, DownloadProgress>> = engine.progress

    private val _bytes = MutableStateFlow(0L)

    /** Space used, refreshed by [measure]. */
    val bytes: StateFlow<Long> = _bytes.asStateFlow()

    init {
        scope.launch {
            reload()
            // A song saved here that the engine never heard of, its start lost to a closed app,
            // is asked for again.
            engine.loaded.first { it }
            val known = engine.progress.value
            _songs.value.filter { it.id !in known && engine.supports(it) }.forEach(engine::start)
        }
    }

    /** Whether [song] is downloaded in full, so it plays with no network. */
    fun isDone(song: Song): Boolean = engine.progress.value[song.id]?.status == DownloadStatus.Done

    fun supports(song: Song) = engine.supports(song)

    /** Downloads the songs that can be, skipping the ones already saved; a failed one starts again. */
    fun download(songs: List<Song>) {
        val saved = _songs.value.mapTo(HashSet(), Song::id)
        val failed = engine.progress.value.filterValues { it.status == DownloadStatus.Failed }.keys
        val fresh = songs.filter { engine.supports(it) && (it.id !in saved || it.id in failed) }
        if (fresh.isEmpty()) return
        scope.launch {
            store.transaction { db ->
                fresh.forEach { song ->
                    db.putSong(song)
                    db.insertWithOnConflict("downloads", null, ContentValues().apply {
                        put("song_id", song.id)
                        put("added_at", System.currentTimeMillis())
                    }, SQLiteDatabase.CONFLICT_IGNORE)
                }
            }
            fresh.forEach(engine::start)
            reload()
        }
    }

    fun remove(song: Song) = removeAll(listOf(song))

    fun removeAll(songs: List<Song> = _songs.value) {
        scope.launch {
            store.transaction { db -> songs.forEach { db.delete("downloads", "song_id = ?", arrayOf(it.id)) } }
            songs.forEach { engine.remove(it.id) }
            reload()
            measure()
        }
    }

    /** Reads how much space the downloads take. */
    fun measure() {
        scope.launch { _bytes.value = store.run { engine.bytesUsed() } }
    }

    private suspend fun reload() {
        _songs.value = store.run { db ->
            db.rawQuery("SELECT $SONG_COLUMNS FROM downloads d JOIN songs s ON s.id = d.song_id ORDER BY d.added_at DESC", null)
                .all { it.readSong() }
        }
    }
}
