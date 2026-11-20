package app.hihu

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import app.hihu.music.Song
import app.hihu.music.StreamResolver
import app.hihu.state.DownloadEngine
import app.hihu.state.DownloadProgress
import app.hihu.state.DownloadStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.Executors

/**
 * Downloads on Media3's DownloadManager, into a cache in the app's own storage that nothing else
 * can read and that goes with the app when it is uninstalled. The player reads through the same
 * cache, so a downloaded song plays with no network. One instance per process: a cache folder
 * can only be opened once.
 */
@OptIn(UnstableApi::class)
class Offline(
    private val context: Context,
    resolvers: List<StreamResolver>,
    /** Told the URI of each song as its download starts, so its loudness is known offline. */
    private val onDownload: (String) -> Unit = {},
) : DownloadEngine {
    private val schemes = resolvers.associateBy { it.scheme }
    private val database = StandaloneDatabaseProvider(context)
    private val cache = SimpleCache(File(context.filesDir, FOLDER), NoOpCacheEvictor(), database)

    /**
     * Local files and plain URLs open as they are. A provider URI such as `hihu-yt://<id>` is
     * resolved to its stream URL and headers on the loading thread, every time it is opened.
     */
    private val upstream: DataSource.Factory = run {
        val http = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
        // YouTube streams are read in chunks; YouTube slows a request for the whole file.
        val resolving = ResolvingDataSource.Factory(DefaultDataSource.Factory(context, ChunkedStreamDataSource.Factory(http))) { spec ->
            val resolver = schemes[spec.uri.scheme] ?: return@Factory spec
            val stream = resolver.resolve(spec.uri.toString())
            spec.withUri(Uri.parse(stream.url)).withAdditionalHeaders(stream.headers)
        }
        // A URL YouTube stops accepting mid-song is fetched again on the player's retry.
        RefreshingDataSource.Factory(resolving) { uri -> schemes[uri.scheme]?.invalidate(uri.toString()) }
    }

    /**
     * What the player reads through: a downloaded song from the cache, without resolving or any
     * network; anything else from its source, streamed and not kept.
     */
    val playerSource: DataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(upstream)
        .setCacheWriteDataSinkFactory(null)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    val manager = DownloadManager(
        context,
        database,
        cache,
        CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(upstream),
        Executors.newFixedThreadPool(PARALLEL),
    ).apply {
        maxParallelDownloads = PARALLEL
        addListener(object : DownloadManager.Listener {
            override fun onInitialized(downloadManager: DownloadManager) = readIndex()
            override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) {
                when (val progress = download.toProgress()) {
                    null -> known.remove(download.request.id)
                    else -> known[download.request.id] = progress
                }
                refresh()
            }
            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                known.remove(download.request.id)
                refresh()
            }
        })
    }

    private val _progress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    override val progress: StateFlow<Map<String, DownloadProgress>> = _progress.asStateFlow()

    /** Every download's last known state, read from the index once and then kept from the listener. */
    private val known = HashMap<String, DownloadProgress>()

    // DownloadManager reports changes of state, not of percentage, so progress is read on a timer
    // while something downloads, from the running downloads in memory.
    private val main = Handler(Looper.getMainLooper())
    private val tick = Runnable { refresh() }

    override fun supports(song: Song): Boolean = Uri.parse(song.uri).scheme in schemes

    override fun start(song: Song) {
        val request = DownloadRequest.Builder(song.id, Uri.parse(song.uri))
            // The player looks songs up by their URI, so the download is stored under it too.
            .setCustomCacheKey(song.uri)
            .build()
        DownloadService.sendAddDownload(context, HihuDownloadService::class.java, request, false)
        onDownload(song.uri)
    }

    override fun remove(songId: String) {
        DownloadService.sendRemoveDownload(context, HihuDownloadService::class.java, songId, false)
    }

    override fun bytesUsed(): Long = cache.cacheSpace

    /** Reads the stored downloads once, when the manager is ready. */
    private fun readIndex() {
        manager.downloadIndex.getDownloads().use { cursor ->
            while (cursor.moveToNext()) cursor.download.toProgress()?.let { known[cursor.download.request.id] = it }
        }
        refresh()
    }

    /** Publishes every download's state into [progress], and keeps doing so while any is running. */
    private fun refresh() {
        val all = HashMap(known)
        manager.currentDownloads.forEach { download -> download.toProgress()?.let { all[download.request.id] = it } }
        _progress.value = all
        main.removeCallbacks(tick)
        if (all.values.any { it.status == DownloadStatus.Downloading || it.status == DownloadStatus.Queued }) {
            main.postDelayed(tick, TICK_MS)
        }
    }

    private fun Download.toProgress(): DownloadProgress? = when (state) {
        Download.STATE_COMPLETED -> DownloadProgress(DownloadStatus.Done, PERCENT_DONE)
        Download.STATE_DOWNLOADING -> DownloadProgress(DownloadStatus.Downloading, percentDownloaded.coerceAtLeast(0f))
        Download.STATE_FAILED -> DownloadProgress(DownloadStatus.Failed)
        Download.STATE_QUEUED, Download.STATE_STOPPED, Download.STATE_RESTARTING -> DownloadProgress(DownloadStatus.Queued)
        else -> null
    }

    private companion object {
        const val FOLDER = "downloads"
        const val PARALLEL = 2
        const val TICK_MS = 1000L
        const val PERCENT_DONE = 100f
    }
}
