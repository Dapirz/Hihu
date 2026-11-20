package app.hihu.music

/** What a search looks for. Songs and videos play directly; albums and artists open a list. */
enum class SearchKind { Songs, Videos, Albums, Artists }

/** One search hit: a song to play, or an album or artist to open. */
sealed interface SearchResult {
    /** Stable and unique within one list of results. */
    val key: String

    data class Track(val song: Song) : SearchResult {
        override val key: String get() = song.id
    }

    data class Group(val collection: Collection) : SearchResult {
        override val key: String get() = collection.id
    }
}

/** A catalogue that can be searched, such as YouTube Music. Callers run it off the main thread. */
interface MusicSearch {
    /** The first page of results for [query] of the given [kind]. */
    suspend fun search(query: String, kind: SearchKind): Page<SearchResult>

    /** Completions for a partly typed [query], best first. */
    suspend fun suggestions(query: String): List<String>
}

/**
 * Songs like a given one, chosen by the provider, for a radio that keeps playing after it.
 * YouTube Music calls this a mix.
 */
interface MusicRadio {
    /** Whether [song] came from this provider, so it has a radio here. */
    fun hasRadio(song: Song): Boolean

    /** The first page of songs similar to [song], without [song] itself. The radio does not end. */
    suspend fun radio(song: Song): Page<Song>
}

/** Reads the songs inside a [Collection] that the same provider returned. */
interface MusicBrowse {
    /** The first page of songs in [collection]. */
    suspend fun songs(collection: Collection): Page<Song>
}
