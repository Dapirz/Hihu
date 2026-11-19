package com.daffa.hihu.music

/** What a [Collection] is, which decides how it looks and what [count] means. */
enum class CollectionKind { Album, Playlist, Artist }

/**
 * A group of songs from a provider: an album, a playlist or an artist. [id] is the provider's own reference
 * and is only meaningful to the provider that made it. [count] is the number of songs for an
 * album or playlist and the number of subscribers for an artist, or null when unknown.
 */
data class Collection(
    val id: String,
    val kind: CollectionKind,
    val title: String,
    val subtitle: String?,
    val artworkUri: String?,
    val count: Long? = null,
)
