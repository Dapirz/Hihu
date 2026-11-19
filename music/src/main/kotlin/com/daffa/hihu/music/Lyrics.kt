package com.daffa.hihu.music

/** One line of synced lyrics and when it starts, in milliseconds from the start of the song. */
data class LyricLine(val timeMs: Long, val text: String)

/**
 * A song's lyrics. [synced] has the lines with their times when the source knows them; [plain]
 * is the whole text without times. At least one of them is set.
 */
data class Lyrics(val synced: List<LyricLine>?, val plain: String?, val source: String)

/** Finds lyrics for a song. Callers run it off the main thread. */
interface LyricsProvider {
    /** The lyrics of [song], or null when the provider has none. Throws when it cannot be reached. */
    suspend fun lyrics(song: Song): Lyrics?
}
