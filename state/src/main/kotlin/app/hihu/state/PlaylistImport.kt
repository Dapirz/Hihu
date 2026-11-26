package app.hihu.state

import android.util.Log
import app.hihu.music.ContentUnavailable
import app.hihu.music.PlaylistLinks
import app.hihu.music.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Why an import stopped. */
enum class ImportFailure {
    /** The text has no playlist link in it. */
    NotAPlaylist,

    /** The playlist is private, deleted or blocked here. */
    Unavailable,

    /** The playlist had songs, but none Hihu can play. */
    Empty,

    /** The connection failed partway; nothing was saved. */
    Network,
}

/** Where an import is. */
sealed interface ImportState {
    data object Idle : ImportState

    /** Reading pages: [fetched] songs so far, out of [total] when the provider says. */
    data class Reading(val title: String?, val fetched: Int, val total: Long?) : ImportState

    /** Saved as playlist [id], called [name], with [count] songs. */
    data class Done(val id: Long, val name: String, val count: Int) : ImportState

    data class Failed(val reason: ImportFailure) : ImportState
}

/**
 * Copies a shared playlist into a new playlist of Hihu's own, reading every page first so a
 * 400-song list comes over whole. Nothing is saved until the last page has been read. A shared
 * link that arrives from outside the app waits in [pending] until the import dialog takes it.
 */
class PlaylistImport(
    private val source: PlaylistLinks,
    private val playlists: Playlists,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    private val _pending = MutableStateFlow<String?>(null)

    /** A link shared to Hihu from another app, for the UI to offer importing. */
    val pending: StateFlow<String?> = _pending.asStateFlow()

    private var job: Job? = null

    /** Holds [text] from the share sheet until the UI shows it; ignored without a link in it. */
    fun offer(text: String) {
        linkIn(text)?.let { _pending.value = it }
    }

    /** The UI has taken the pending link. */
    fun takePending() {
        _pending.value = null
    }

    /** Imports the playlist whose link is somewhere in [text], replacing an import in progress. */
    fun start(text: String) {
        job?.cancel()
        val link = linkIn(text) ?: run {
            _state.value = ImportState.Failed(ImportFailure.NotAPlaylist)
            return
        }
        _state.value = ImportState.Reading(null, 0, null)
        job = scope.launch {
            _state.value = try {
                read(link)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ContentUnavailable) {
                Log.w(TAG, "import: playlist unavailable", e)
                ImportState.Failed(ImportFailure.Unavailable)
            } catch (e: Exception) {
                Log.w(TAG, "import: cannot read playlist", e)
                ImportState.Failed(ImportFailure.Network)
            }
        }
    }

    /** Stops an import in progress, saving nothing, or forgets a finished one. */
    fun reset() {
        job?.cancel()
        job = null
        _state.value = ImportState.Idle
    }

    private suspend fun read(link: String): ImportState {
        val linked = source.playlist(link) ?: return ImportState.Failed(ImportFailure.NotAPlaylist)
        val title = linked.collection.title
        val total = linked.collection.count
        val songs = LinkedHashMap<String, Song>()
        var page = linked.songs
        while (true) {
            val before = songs.size
            page.items.forEach { songs.putIfAbsent(it.id, it) }
            _state.value = ImportState.Reading(title, songs.size, total)
            val next = page.next ?: break
            // A mix goes on for ever and repeats itself; stop once a page brings nothing new.
            if (songs.size == before || songs.size >= MAX_SONGS) break
            page = next()
        }
        if (songs.isEmpty()) return ImportState.Failed(ImportFailure.Empty)
        val name = title.ifBlank { DEFAULT_NAME }
        val id = playlists.createNow(name, songs.values.toList())
        return ImportState.Done(id, name, songs.size)
    }

    private companion object {
        const val TAG = "Hihu"
        const val MAX_SONGS = 5000
        const val DEFAULT_NAME = "YouTube"
    }
}

private val LINK = Regex("""https?://\S+""")

/** The first web link in [text], which a share from YouTube wraps in a sentence. */
internal fun linkIn(text: String): String? = LINK.find(text)?.value
