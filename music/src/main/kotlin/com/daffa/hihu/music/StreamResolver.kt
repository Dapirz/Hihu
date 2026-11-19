package com.daffa.hihu.music

/**
 * Turns a provider's song URI (for example `hihu-yt://<id>`) into something the player can open.
 * The player calls [resolve] on its loading thread every time it opens the song, so an
 * implementation caches what it can.
 */
interface StreamResolver {
    /** The URI scheme of the songs this resolver handles. */
    val scheme: String

    /** Blocks until the stream for [uri] is known. Throws when it cannot be played. */
    fun resolve(uri: String): ResolvedStream
}

/** A playable URL and the HTTP headers the server expects with it. */
data class ResolvedStream(val url: String, val headers: Map<String, String> = emptyMap())
