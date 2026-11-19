package com.daffa.hihu.music

/** A playlist found from a shared link: what it is, and the first page of its songs. */
class LinkedPlaylist(val collection: Collection, val songs: Page<Song>)

/** The link points at something that exists but cannot be read: private, deleted or blocked. */
class ContentUnavailable(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Opens playlists from the links people share, such as `youtube.com/playlist?list=…`. */
interface PlaylistLinks {
    /**
     * The playlist [link] points at, or null when it is not a playlist link this provider knows.
     * Throws [ContentUnavailable] for a playlist that is private or gone, and other exceptions
     * when the network fails. Callers run it off the main thread.
     */
    suspend fun playlist(link: String): LinkedPlaylist?
}
