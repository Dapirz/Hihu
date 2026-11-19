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

/**
 * Asks [providers] in order and keeps the first lyrics found. A provider that cannot be reached
 * is skipped; only when none found anything and one of them failed does this throw, so the
 * caller can offer to try again instead of saying there are no lyrics.
 */
class FirstLyrics(private val providers: List<LyricsProvider>) : LyricsProvider {
    override suspend fun lyrics(song: Song): Lyrics? {
        var failure: Exception? = null
        for (provider in providers) {
            try {
                provider.lyrics(song)?.let { return it }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = failure ?: e
            }
        }
        failure?.let { throw it }
        return null
    }
}
