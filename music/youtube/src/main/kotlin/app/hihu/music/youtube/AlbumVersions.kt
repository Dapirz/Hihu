package app.hihu.music.youtube

import app.hihu.music.Song
import java.util.Locale

/**
 * The rules that tell whether a song and a music video are the same recording, so a live take,
 * a remix or another artist's cover never stands in for the video. Only a sure match counts.
 */
internal object AlbumVersions {
    /** How many of a search's first hits are weighed; further down, a hit is more likely another recording. */
    const val WEIGHED = 5

    /** How much longer than its song a music video may run, at the least: an intro or a skit. */
    private const val LONGER_MS = 30_000L

    /** What separates two names in a credit line. */
    private val CREDIT_BREAK = Regex("""\s*(?:,|&|\b(?:feat|ft)\b\.?|\bfeaturing\b)\s*""",RegexOption.IGNORE_CASE)

    /** Words in a bracketed part of a video title that only mark it as a video. */
    private val VIDEO_MARKS = setOf(
        "official", "video", "mv", "visualizer", "visualiser", "lyric", "lyrics",
        "audio", "hd", "4k", "hq", "clip", "music",
    )

    /** What to search for to find the album version of [video]: its title as a song is called, and its first artist. */
    fun songQuery(video: Song): String? {
        val reading = readings(video).first()
        val artist = reading.artists.firstOrNull() ?: return null
        return "${reading.title} $artist"
    }

    /** What to search for to find the music video of [song]: its title and its first artist. */
    fun videoQuery(song: Song): String? {
        val artist = artists(song).firstOrNull() ?: return null
        return "${song.title} $artist"
    }

    /**
     * Whether [song] is the recording [video] shows: the same title once the video's own markings
     * are off, every artist of the video credited on the song, and a length that fits. A video
     * can run longer than its song, or be cut down from it.
     */
    fun sameSong(video: Song, song: Song): Boolean {
        val fits = video.durationMs > 0 && song.durationMs > 0 &&
            song.durationMs + maxOf(LONGER_MS, video.durationMs / 5) >= video.durationMs &&
            song.durationMs <= video.durationMs * 2
        if (!fits) return false
        val title = plain(song.title)
        val credited = artists(song)
        return readings(video).any { reading ->
            val wanted = plain(reading.title)
            wanted.isNotEmpty() && wanted == title && reading.artists.isNotEmpty() && reading.artists.all { it in credited }
        }
    }

    /** A way to read a video's title and credit as a song's: its title as the song would be called, and its artists. */
    private class Reading(val title: String, val artists: List<String>)

    /**
     * How [video] may be read as a song, likeliest first. A title that leads with "Artist - "
     * names the artist, which matters when a label uploaded the video; when the lead is not the
     * uploader, it may also be part of the title, so both readings are tried.
     */
    private fun readings(video: Song): List<Reading> {
        val uploader = names(video.artist.orEmpty())
        val dash = video.title.indexOf(" - ")
        val rest = video.title.substring(dash + 3).takeIf { dash >= 0 }?.takeIf { it.isNotBlank() }
            ?: return listOf(Reading(songTitle(video.title), uploader))
        val lead = video.title.substring(0, dash)
        if (words(lead) in uploader) return listOf(Reading(songTitle(rest), uploader))
        return listOf(Reading(songTitle(rest), names(lead)), Reading(songTitle(video.title), uploader))
    }

    /** [title] without the brackets that only say it is a video. */
    private fun songTitle(title: String): String {
        val kept = StringBuilder()
        val part = StringBuilder()
        var depth = 0
        for (c in title) {
            when {
                c == '(' || c == '[' -> {
                    depth++
                    when (depth == 1) {
                        true -> part.clear()
                        false -> part.append(c)
                    }
                }
                (c == ')' || c == ']') && depth > 0 -> {
                    depth--
                    when {
                        depth > 0 -> part.append(c)
                        marksVideo(part.toString()) -> Unit
                        else -> kept.append('(').append(part).append(')')
                    }
                }
                depth > 0 -> part.append(c)
                else -> kept.append(c)
            }
        }
        return kept.toString().trim()
    }

    /** Whether a bracketed part only marks a title as a video, as "Official Music Video" does. */
    private fun marksVideo(part: String): Boolean {
        // "M/V" comes apart into two letters once its slash is gone.
        val plainPart = words(part).replace("m v", "mv")
        return plainPart.isNotEmpty() && plainPart.split(' ').all { it in VIDEO_MARKS }
    }

    /** A title reduced to what tells recordings apart, with a featured artist or a remaster left out. */
    private fun plain(title: String): String {
        val kept = StringBuilder()
        val part = StringBuilder()
        var depth = 0
        for (c in title) {
            when {
                c == '(' || c == '[' -> {
                    depth++
                    part.clear()
                }
                (c == ')' || c == ']') && depth > 0 -> {
                    depth--
                    if (depth == 0 && !harmless(part.toString())) kept.append(' ').append(part)
                }
                depth > 0 -> part.append(c)
                else -> kept.append(c)
            }
        }
        return words(kept.toString())
    }

    /** Whether a bracketed part leaves the recording as it is: a guest credit or a remaster. */
    private fun harmless(part: String): Boolean {
        val plainPart = words(part)
        return plainPart.substringBefore(' ') in setOf("feat", "ft", "featuring", "with") || "remaster" in plainPart
    }

    /** The artists credited on [song], as compared names. */
    private fun artists(song: Song): List<String> = names(song.artist.orEmpty())

    /** Each name in a credit line such as "A, B & C" or "A ft. B", without a channel's "VEVO" or " - Topic". */
    private fun names(line: String): List<String> = line.split(CREDIT_BREAK)
        .map { it.trim().removeSuffix(" - Topic").removeSuffix("VEVO").let(::words) }
        .filter { it.isNotEmpty() }

    /** Lowercase letters and digits, one space between words, so punctuation and case never tell two names apart. */
    private fun words(text: String): String = text
        .map { if (it.isLetterOrDigit()) it else ' ' }
        .joinToString("")
        .lowercase(Locale.ROOT)
        .split(' ')
        .filter { it.isNotEmpty() }
        .joinToString(" ")
}
