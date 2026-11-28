package app.hihu.state

import android.util.Log
import app.hihu.music.ContentUnavailable
import app.hihu.music.ForeignPlaylists
import app.hihu.music.ForeignTrack
import app.hihu.music.MusicSearch
import app.hihu.music.NotAPlaylist
import app.hihu.music.PlaylistLinks
import app.hihu.music.SongMatch
import app.hihu.music.UnreadablePage
import app.hihu.music.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

    /** The link is to one song, an artist or a show, not to a list of songs. */
    NotASongList,

    /** The other service's page no longer reads as Hihu expects. */
    Unreadable,
}

/** Where an import is. */
sealed interface ImportState {
    data object Idle : ImportState

    /** Reading pages: [fetched] songs so far, out of [total] when the provider says. */
    data class Reading(val title: String?, val fetched: Int, val total: Long?) : ImportState

    /** Finding the songs of another service's playlist: [done] of [total] looked for so far. */
    data class Matching(val title: String, val done: Int, val total: Int) : ImportState

    /**
     * Saved as playlist [id], called [name], with [count] songs. From another service, [unsure]
     * names the songs that may be another version and [missing] those not found, as
     * "Title – Artist"; [cut] is (read, in all) when the service gave only the first part.
     */
    data class Done(
        val id: Long,
        val name: String,
        val count: Int,
        val unsure: List<String> = emptyList(),
        val missing: List<String> = emptyList(),
        val cut: Pair<Int, Int>? = null,
    ) : ImportState

    data class Failed(val reason: ImportFailure) : ImportState
}

/**
 * Copies a shared playlist into a new playlist of Hihu's own, reading every page first so a
 * 400-song list comes over whole. A playlist or album from a service Hihu cannot play from, like
 * Spotify, is read through [foreign] and each of its songs looked for through [search], a few
 * at a time. Nothing is saved until the last song is in. A shared link that arrives from outside
 * the app waits in [pending] until the import dialog takes it.
 */
class PlaylistImport(
    private val source: PlaylistLinks,
    private val foreign: List<ForeignPlaylists>,
    private val search: MusicSearch,
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
                when (val service = foreign.firstOrNull { it.handles(link) }) {
                    null -> read(link)
                    else -> readForeign(service, link)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: NotAPlaylist) {
                Log.w(TAG, "import: not a list of songs")
                ImportState.Failed(ImportFailure.NotASongList)
            } catch (e: UnreadablePage) {
                Log.w(TAG, "import: cannot read the other service's page", e)
                ImportState.Failed(ImportFailure.Unreadable)
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

    /**
     * Reads another service's list and finds each song in Hihu's catalogue. A sure match goes in,
     * an unsure one goes in and is named, and a song not found is named and left out.
     */
    private suspend fun readForeign(service: ForeignPlaylists, link: String): ImportState {
        val list = service.read(link)
        val title = list.name.ifBlank { FOREIGN_NAME }
        val total = list.tracks.size
        var done = 0
        _state.value = ImportState.Matching(title, 0, total)
        val gate = Semaphore(PARALLEL)
        val matches = coroutineScope {
            list.tracks.map { track ->
                async {
                    gate.withPermit {
                        val match = matchOnce(track)
                        done++
                        _state.value = ImportState.Matching(title, done, total)
                        track to match
                    }
                }
            }.awaitAll()
        }
        val songs = LinkedHashMap<String, Song>()
        val unsure = mutableListOf<String>()
        val missing = mutableListOf<String>()
        for ((track, match) in matches) {
            val label = listOfNotNull(track.title, track.artists.firstOrNull()).joinToString(" – ")
            when {
                match == null -> missing += label
                songs.putIfAbsent(match.song.id, match.song) == null && !match.sure -> unsure += label
            }
        }
        missing += list.skipped
        if (songs.isEmpty()) return ImportState.Failed(ImportFailure.Empty)
        val read = total + list.skipped.size
        val cut = list.total?.takeIf { it > read }?.let { read to it }
        val id = playlists.createNow(title, songs.values.toList())
        return ImportState.Done(id, title, songs.size, unsure, missing, cut)
    }

    /** Looks for [track], once more after a moment if the first try fails, as a dropped connection may. */
    private suspend fun matchOnce(track: ForeignTrack): SongMatch? = try {
        search.matchSong(track)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        delay(RETRY_MS)
        search.matchSong(track)
    }

    private companion object {
        const val TAG = "Hihu"
        const val MAX_SONGS = 5000
        const val DEFAULT_NAME = "YouTube"
        const val FOREIGN_NAME = "Spotify"
        /** How many songs are looked for at once: quick, without crowding the catalogue. */
        const val PARALLEL = 4
        const val RETRY_MS = 1_000L
    }
}

private val LINK = Regex("""https?://\S+|spotify:(?:playlist|album|track):[A-Za-z0-9]+""")

/** The first link in [text], which a share wraps in a sentence; Spotify's own `spotify:` links count too. */
internal fun linkIn(text: String): String? = LINK.find(text)?.value
