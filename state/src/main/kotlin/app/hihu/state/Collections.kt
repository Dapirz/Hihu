package app.hihu.state

import app.hihu.music.Collection
import app.hihu.music.MusicBrowse
import app.hihu.music.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/** An open album or artist: what it is, and its songs as they load. */
class OpenCollection(val collection: Collection, private val pager: Pager<Song>) {
    val songs: StateFlow<Paged<Song>> = pager.state

    fun loadMore() = pager.loadMore()

    fun retry() = pager.retry()
}

/**
 * The albums and artists the user has opened, by id, so going back to one shows it again
 * without reloading. Only the most recent few are kept.
 */
class Collections(private val source: MusicBrowse, private val scope: CoroutineScope) {
    private val open = object : LinkedHashMap<String, OpenCollection>(MAX_OPEN, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, OpenCollection>) = size > MAX_OPEN
    }

    // Only the small descriptions are kept for good, so a dropped collection can be reopened when
    // the user goes back to it.
    private val known = HashMap<String, Collection>()

    /** Opens [collection], starting to load its songs unless it is already open. */
    fun open(collection: Collection): OpenCollection {
        known[collection.id] = collection
        return open.getOrPut(collection.id) {
            val pager = Pager<Song>(scope, Song::id, "collection")
            pager.load { source.songs(collection) }
            OpenCollection(collection, pager)
        }
    }

    /** The collection under [id], reopened if it was dropped, or null if it was never opened. */
    operator fun get(id: String): OpenCollection? = open[id] ?: known[id]?.let(::open)

    private companion object {
        const val MAX_OPEN = 8
        const val LOAD_FACTOR = 0.75f
    }
}
