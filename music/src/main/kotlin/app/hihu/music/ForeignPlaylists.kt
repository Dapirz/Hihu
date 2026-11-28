package app.hihu.music

/** A song in another service's playlist: only what that service shows of it, not a playable song. */
data class ForeignTrack(val title: String, val artists: List<String>, val durationMs: Long)

/**
 * A playlist or album read from another service, such as Spotify. [total] is how many items
 * the service says it holds, when it says; more than [tracks] and [skipped] hold together means
 * only the first part could be read. [skipped] names the items that are no songs, like podcast
 * episodes, as "Title – Artist".
 */
class ForeignPlaylist(
    val name: String,
    val tracks: List<ForeignTrack>,
    val total: Int?,
    val skipped: List<String> = emptyList(),
    /** True when the service gave only the first part, whether or not it said how much there is. */
    val truncated: Boolean = false,
)

/** Reads playlists shared from a service Hihu cannot play from, so their songs can be found elsewhere. */
interface ForeignPlaylists {
    /** Whether [link] belongs to this service, playlist or not. */
    fun handles(link: String): Boolean

    /**
     * The playlist or album [link] points at. Throws [NotAPlaylist] for a link to one song,
     * [ContentUnavailable] for a list that is private or gone, [UnreadablePage] when the service's
     * page no longer reads as expected, and other exceptions when the network fails. Callers run
     * it off the main thread.
     */
    suspend fun read(link: String): ForeignPlaylist
}

/** The link points at one song, or something else that is not a list of songs. */
class NotAPlaylist(message: String) : Exception(message)

/** The service's page came back, but not in the shape Hihu knows how to read. */
class UnreadablePage(message: String, cause: Throwable? = null) : Exception(message, cause)

/** A song found for a [ForeignTrack]: [sure] when its length fits too, not only its title and artist. */
class SongMatch(val song: Song, val sure: Boolean)
