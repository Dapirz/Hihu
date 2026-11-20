package app.hihu.music

/**
 * One playable track, whichever provider it came from. [uri] is what the player opens, and
 * [artworkUri] is a URI the UI can load a cover from, when the provider has one.
 */
data class Song(
    val id: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val uri: String,
    val artworkUri: String? = null,
)
