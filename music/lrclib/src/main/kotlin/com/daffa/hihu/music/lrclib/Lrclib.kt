package com.daffa.hihu.music.lrclib

import com.daffa.hihu.music.LyricLine
import com.daffa.hihu.music.Lyrics
import com.daffa.hihu.music.LyricsProvider
import com.daffa.hihu.music.Song
import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/**
 * Lyrics from LRCLIB (lrclib.net), a free and open lyrics database that needs no account.
 * Only the title, artist and length of the song are sent. YouTube titles are cleaned first, so
 * "Faint (Official Music Video)" is looked up as "Faint".
 */
class Lrclib : LyricsProvider {

    override suspend fun lyrics(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        val (title, artist) = clean(song)
        val seconds = song.durationMs / 1000
        exact(title, artist, song.album, seconds) ?: search(title, artist, seconds)
    }

    /** LRCLIB's exact lookup, which also matches on length within a couple of seconds. */
    private fun exact(title: String, artist: String?, album: String?, seconds: Long): Lyrics? {
        if (artist == null || seconds <= 0) return null
        val query = buildList {
            add("track_name" to title)
            add("artist_name" to artist)
            if (album != null) add("album_name" to album)
            add("duration" to seconds.toString())
        }
        val body = get("/api/get", query) ?: return null
        return toLyrics(JsonParser.`object`().from(body))
    }

    /** A looser search, keeping the hit closest in length to the song. */
    private fun search(title: String, artist: String?, seconds: Long): Lyrics? {
        val query = buildList {
            add("track_name" to title)
            if (artist != null) add("artist_name" to artist)
        }
        val body = get("/api/search", query) ?: return null
        val hits: JsonArray = JsonParser.array().from(body)
        return hits.filterIsInstance<JsonObject>()
            .filter { seconds <= 0 || abs(it.getDouble("duration") - seconds) <= MAX_LENGTH_GAP_S }
            .minByOrNull { abs(it.getDouble("duration") - seconds) }
            ?.let(::toLyrics)
    }

    private fun toLyrics(json: JsonObject): Lyrics? {
        if (json.getBoolean("instrumental", false)) return null
        val synced = json.getString("syncedLyrics", null)?.let(::parseLrc)?.takeIf { it.isNotEmpty() }
        val plain = json.getString("plainLyrics", null)?.takeIf { it.isNotBlank() }
        if (synced == null && plain == null) return null
        return Lyrics(synced, plain, SOURCE)
    }

    /** GETs [path] with [query]; null on 404, which is how LRCLIB says it has nothing. */
    private fun get(path: String, query: List<Pair<String, String>>): String? {
        val params = query.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        val connection = URL("$BASE$path?$params").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            return when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> error("lrclib: HTTP $code")
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val BASE = "https://lrclib.net"
        const val SOURCE = "LRCLIB"
        // LRCLIB asks clients to name themselves; this names the app and nothing about the user.
        const val USER_AGENT = "Hihu/0.1.0"
        const val TIMEOUT_MS = 10_000
        const val MAX_LENGTH_GAP_S = 5
    }
}

/**
 * The title and artist to look up. YouTube titles carry noise like "(Official Video)" or
 * "Artist - Title", and channel names like "Official Arctic Monkeys".
 */
internal fun clean(song: Song): Pair<String, String?> {
    var title = song.title.replace(NOISE, "").trim()
    val artist = song.artist?.removePrefix("Official ")?.removeSuffix(" - Topic")?.trim()
    val dash = title.indexOf(" - ")
    if (dash > 0) {
        val left = title.substring(0, dash).trim()
        val right = title.substring(dash + 3).trim()
        title = when {
            artist != null && left.equals(artist, ignoreCase = true) -> right
            artist != null && right.equals(artist, ignoreCase = true) -> left
            else -> title
        }
    }
    return title.ifEmpty { song.title } to artist?.ifEmpty { null }
}

/** Bracketed parts of a title that describe the upload rather than the song. */
private val NOISE = Regex(
    """\s*[(\[][^)\]]*(official|video|lyric|audio|visuali[sz]er|mv|hd|4k|remaster)[^)\]]*[)\]]""",
    RegexOption.IGNORE_CASE,
)

/** Reads LRC text: `[mm:ss.xx]line`, where a line may carry several times. Tags are skipped. */
internal fun parseLrc(text: String): List<LyricLine> = text.lineSequence().flatMap { raw ->
    val times = TIME.findAll(raw).toList()
    if (times.isEmpty()) return@flatMap emptySequence()
    val words = raw.substring(times.last().range.last + 1).trim()
    times.asSequence().map { match ->
        val (min, sec, frac) = match.destructured
        val ms = min.toLong() * 60_000 + sec.toLong() * 1000 + frac.padEnd(3, '0').take(3).toLong()
        LyricLine(ms, words)
    }
}.sortedBy { it.timeMs }.toList()

private val TIME = Regex("""\[(\d{1,3}):(\d{2})[.:](\d{1,3})]""")
