package app.hihu.state

import android.util.Log
import app.hihu.music.Collection
import app.hihu.music.MusicFeed
import java.util.Calendar
import kotlinx.coroutines.flow.asStateFlow
import app.hihu.music.MusicRadio
import app.hihu.music.Shelf
import app.hihu.music.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A home row made on the phone from Hihu's own history and favourites, needing no account. */
sealed interface PersonalRow {
    val songs: List<Song>

    /** What was played lately, most recent first. */
    data class KeepListening(override val songs: List<Song>) : PersonalRow

    /** What was played most in the last month. */
    data class MostPlayed(override val songs: List<Song>) : PersonalRow

    /** Hearted songs that have not been played lately. */
    data class Forgotten(override val songs: List<Song>) : PersonalRow

    /** Songs like the artist played most lately, from that artist's radio. */
    data class Similar(val artist: String, override val songs: List<Song>) : PersonalRow
}

/**
 * The Home tab: rows made from Hihu's own history and favourites, then YouTube Music's home
 * shelves, which are the same for everyone in the country. Fetched the first time Home opens.
 */
class Home(
    private val feed: MusicFeed,
    private val radio: MusicRadio,
    history: History,
    favorites: Favorites,
    private val hidden: StateFlow<Set<String>>,
    private val scope: CoroutineScope,
) {
    private val pager = Pager<Shelf>(scope, Shelf::title, "home")

    /** YouTube Music's shelves, a page at a time. */
    val shelves: StateFlow<Paged<Shelf>> = pager.state

    private val similar = MutableStateFlow<PersonalRow.Similar?>(null)
    private var similarJob: Job? = null
    private var similarSeed: String? = null

    /** The rows made on the phone, in the order they show; empty ones are left out. */
    val personal: StateFlow<List<PersonalRow>> =
        combine(history.recent, history.mostPlayed, favorites.songs, similar, hidden) { recent, most, hearted, like, gone ->
            val lately = recent.take(LATELY).mapTo(HashSet(), Song::id)
            listOfNotNull(
                // A hidden song stays in the history, so every row leaves it out here.
                recent.filter { it.id !in gone }.take(ROW_SIZE).takeIf { it.isNotEmpty() }?.let(PersonalRow::KeepListening),
                most.filter { it.id !in gone }.takeIf { it.isNotEmpty() }?.let(PersonalRow::MostPlayed),
                hearted.filter { it.id !in lately && it.id !in gone }.take(ROW_SIZE).takeIf { it.isNotEmpty() }?.let(PersonalRow::Forgotten),
                like?.let { row -> row.copy(songs = row.songs.filter { it.id !in gone }) }?.takeIf { it.songs.isNotEmpty() },
            )
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val recentSongs = history.recent
    private val history = history

    private val _releases = MutableStateFlow<List<Collection>>(emptyList())

    /**
     * This year's and last year's albums and singles by the artists played most lately, newest
     * first; empty until loaded, or when there are none.
     */
    val releases: StateFlow<List<Collection>> = _releases.asStateFlow()
    private var releasesJob: Job? = null
    private var releasesAt = 0L

    /** Loads the shelves and the similar-artist row, unless they already have. */
    fun open() {
        val now = pager.state.value
        // A first page that failed, or came empty, is asked for again rather than left so.
        if (!now.loading && (!now.loaded || now.failed || now.items.isEmpty())) pager.load { feed.home() }
        loadSimilar()
        loadReleases()
    }

    fun refresh() {
        pager.load { feed.home() }
        similarSeed = null
        loadSimilar()
        releasesAt = 0L
        loadReleases()
    }

    /** Looks up the newest releases of the artists played most, at most once a day. */
    private fun loadReleases() {
        if (System.currentTimeMillis() - releasesAt < RELEASES_EVERY_MS || releasesJob?.isActive == true) return
        releasesJob = scope.launch {
            try {
                val artists = history.topArtists(RELEASE_DAYS, RELEASE_ARTISTS)
                if (artists.isEmpty()) return@launch
                val since = Calendar.getInstance().get(Calendar.YEAR) - 1
                _releases.value = feed.latestReleases(artists)
                    .filter { it.year >= since }
                    .sortedByDescending { it.year }
                    // An album often comes twice, as the clean and the explicit version; one is enough.
                    .distinctBy { it.collection.title.trim().lowercase() }
                    .take(ROW_SIZE)
                    .map { it.collection }
                releasesAt = System.currentTimeMillis()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "home: cannot load new releases", e)
            }
        }
    }

    fun loadMore() = pager.loadMore()

    /** Builds the "similar to" row from the artist heard most among the recent songs. */
    private fun loadSimilar() {
        val recent = recentSongs.value.take(LATELY).filter(radio::hasRadio)
        val artist = recent.mapNotNull { it.artist }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: return
        val seed = recent.first { it.artist == artist }
        if (seed.id == similarSeed && (similar.value != null || similarJob?.isActive == true)) return
        similarSeed = seed.id
        similarJob?.cancel()
        similarJob = scope.launch {
            try {
                // Not a song already on the rows of history, nor the same song twice, as a song
                // and its music video often come one after the other.
                val heard = recentSongs.value.mapTo(HashSet(), ::sameSong)
                val songs = radio.radio(seed).items.filter { it.id !in hidden.value && heard.add(sameSong(it)) }.take(ROW_SIZE)
                similar.value = PersonalRow.Similar(artist, songs).takeIf { songs.isNotEmpty() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "home: cannot load songs like $artist", e)
            }
        }
    }

    /** What makes two songs the same one to a listener: the title and artist, not the id. */
    private fun sameSong(song: Song) = song.title.trim().lowercase() + "\u0000" + song.artist?.trim()?.lowercase()

    private companion object {
        const val TAG = "Hihu"
        const val ROW_SIZE = 12
        /** How many recent songs count as played lately. */
        const val LATELY = 40
        const val RELEASE_ARTISTS = 5
        const val RELEASE_DAYS = 60L
        const val RELEASES_EVERY_MS = 24 * 60 * 60 * 1000L
    }
}
