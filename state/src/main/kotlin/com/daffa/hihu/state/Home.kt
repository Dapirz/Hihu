package com.daffa.hihu.state

import android.util.Log
import com.daffa.hihu.music.MusicFeed
import com.daffa.hihu.music.MusicRadio
import com.daffa.hihu.music.Shelf
import com.daffa.hihu.music.Song
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
        combine(history.recent, favorites.songs, similar) { recent, hearted, like ->
            val lately = recent.take(LATELY).mapTo(HashSet(), Song::id)
            listOfNotNull(
                recent.take(ROW_SIZE).takeIf { it.isNotEmpty() }?.let(PersonalRow::KeepListening),
                hearted.filter { it.id !in lately }.take(ROW_SIZE).takeIf { it.isNotEmpty() }?.let(PersonalRow::Forgotten),
                like,
            )
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val recentSongs = history.recent

    /** Loads the shelves and the similar-artist row, unless they already have. */
    fun open() {
        val now = pager.state.value
        if (!now.loaded && !now.loading) pager.load { feed.home() }
        loadSimilar()
    }

    fun refresh() {
        pager.load { feed.home() }
        similarSeed = null
        loadSimilar()
    }

    fun loadMore() = pager.loadMore()

    /** Builds the "similar to" row from the artist heard most among the recent songs. */
    private fun loadSimilar() {
        val recent = recentSongs.value.take(LATELY).filter(radio::hasRadio)
        val artist = recent.mapNotNull { it.artist }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: return
        val seed = recent.first { it.artist == artist }
        if (seed.id == similarSeed && similar.value != null) return
        similarSeed = seed.id
        similarJob?.cancel()
        similarJob = scope.launch {
            try {
                val songs = radio.radio(seed).items.take(ROW_SIZE)
                similar.value = PersonalRow.Similar(artist, songs).takeIf { songs.isNotEmpty() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "home: cannot load songs like $artist", e)
            }
        }
    }

    private companion object {
        const val TAG = "Hihu"
        const val ROW_SIZE = 12
        /** How many recent songs count as played lately. */
        const val LATELY = 40
    }
}
