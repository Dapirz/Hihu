package app.hihu.music

/**
 * A titled row of a feed: songs, or albums, playlists and artists. [ranked] marks a numbered
 * chart, which a screen showing its own chart can leave out.
 */
data class Shelf(val title: String, val items: List<SearchResult>, val ranked: Boolean = false)

/** A mood or genre that groups playlists. [ref] is the provider's reference to its page. */
data class Mood(val title: String, val color: Long?, val ref: String)

/** The Explore page: its shelves, and the moods and genres to browse. */
class ExploreFeed(val shelves: List<Shelf>, val moods: List<Mood>)

/**
 * The feeds a catalogue shows without an account: a home page of shelves, an explore page and
 * pages per mood. None of it is personal. Callers run these off the main thread.
 */
interface MusicFeed {
    /** The first page of home shelves; more follow through [Page.next]. */
    suspend fun home(): Page<Shelf>

    suspend fun explore(): ExploreFeed

    /** The shelves of playlists for one mood or genre. */
    suspend fun mood(mood: Mood): List<Shelf>

    /**
     * The newest albums and singles of each of [artists], by name, newest first per artist. An
     * artist the catalogue cannot find by that exact name is left out.
     */
    suspend fun latestReleases(artists: List<String>): List<Release>
}

/** An album or single, with the year it came out in. */
data class Release(val collection: Collection, val year: Int)
