package app.hihu.music

/** A playlist found from a shared link: what it is, and the first page of its songs. */
class LinkedPlaylist(val collection: Collection, val songs: Page<Song>)

/** The link points at something that exists but cannot be read: private, deleted or blocked. */
class ContentUnavailable(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The links people share: opens playlists from them, such as `youtube.com/playlist?list=…`, and
 * gives the link to share for a song.
 */
interface PlaylistLinks {
    /** The web link to share for [song], or null for a song with none, like a file on the phone. */
    fun songLink(song: Song): String?

    /**
     * The playlist [link] points at, or null when it is not a playlist link this provider knows.
     * Throws [ContentUnavailable] for a playlist that is private or gone, and other exceptions
     * when the network fails. Callers run it off the main thread.
     */
    suspend fun playlist(link: String): LinkedPlaylist?
}
