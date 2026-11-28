package app.hihu.music.spotify

import app.hihu.music.ContentUnavailable
import app.hihu.music.ForeignPlaylist
import app.hihu.music.ForeignPlaylists
import app.hihu.music.ForeignTrack
import app.hihu.music.NotAPlaylist
import app.hihu.music.UnreadablePage
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonParserException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Spotify playlists and albums, read from the embed page Spotify serves to anyone, so no
 * account, key or token is needed and nothing about the user is sent. That page lists the
 * first [EMBED_LIMIT] tracks; the full page says how many there are in all. [version] is
 * Hihu's, for the user agent.
 */
class SpotifyPlaylists(version: String) : ForeignPlaylists {
    // A client that names itself, and nothing about the user.
    private val userAgent = "Hihu/$version"

    override fun handles(link: String): Boolean = SPOTIFY.containsMatchIn(link)

    override suspend fun read(link: String): ForeignPlaylist = withContext(Dispatchers.IO) {
        val (kind, id) = target(link)
        if (kind !in LISTS) throw NotAPlaylist("spotify: a link to a $kind")
        val page = get("$BASE/embed/$kind/$id")?.first ?: throw ContentUnavailable("spotify: no such $kind")
        val props = nextData(page).getObject("props").getObject("pageProps")
        if (props.getInt("status", 200) == HttpURLConnection.HTTP_NOT_FOUND) throw ContentUnavailable("spotify: no such $kind")
        val entity = props.getObject("state").getObject("data").getObject("entity")
        if (entity.isEmpty() || !entity.has("trackList")) throw UnreadablePage("spotify: page changed")
        val tracks = mutableListOf<ForeignTrack>()
        val skipped = mutableListOf<String>()
        entity.getArray("trackList").filterIsInstance<JsonObject>().forEach { item ->
            val title = item.getString("title", "").trim()
            val artists = item.getString("subtitle", "").split(',').map { it.replace(NBSP, ' ').trim() }.filter { it.isNotEmpty() }
            // Episodes and the user's own files are no songs to find.
            when (item.getString("uri", "").startsWith(TRACK_URI) && title.isNotEmpty()) {
                true -> tracks += ForeignTrack(title, artists, item.getLong("duration", 0))
                false -> if (title.isNotEmpty()) skipped += listOfNotNull(title, artists.firstOrNull()).joinToString(" – ")
            }
        }
        val name = entity.getString("name", null)?.takeIf { it.isNotBlank() } ?: entity.getString("title", "")
        ForeignPlaylist(name, tracks, total(kind, id), skipped)
    }

    /**
     * The kind and id a link names: `open.spotify.com/playlist/ID`, with a language part like
     * `intl-id/` or not, `spotify:playlist:ID`, or a short `spotify.link/…`, which redirects to one.
     */
    private fun target(link: String): Pair<String, String> {
        PAGE.find(link)?.let { return it.groupValues[1] to it.groupValues[2] }
        URI.find(link)?.let { return it.groupValues[1] to it.groupValues[2] }
        val short = SHORT.find(link)?.value ?: throw NotAPlaylist("spotify: not a link Hihu knows")
        val (body, landed) = get(short) ?: throw ContentUnavailable("spotify: short link leads nowhere")
        (PAGE.find(landed) ?: PAGE.find(body))?.let { return it.groupValues[1] to it.groupValues[2] }
        throw NotAPlaylist("spotify: the short link leads to no playlist")
    }

    /** How many tracks the full page says the list holds, or null when it does not say. */
    private fun total(kind: String, id: String): Int? = runCatching {
        get("$BASE/$kind/$id")?.first?.let { SONG_COUNT.find(it)?.groupValues?.get(1)?.toIntOrNull() }
    }.getOrNull()

    /** The JSON a page carries for its own scripts. */
    private fun nextData(page: String): JsonObject {
        val json = NEXT_DATA.find(page)?.groupValues?.get(1) ?: throw UnreadablePage("spotify: page changed")
        return try {
            JsonParser.`object`().from(json)
        } catch (e: JsonParserException) {
            throw UnreadablePage("spotify: page changed", e)
        }
    }

    /** GETs [url], following redirects; the body and the address it ended at, or null on 404. */
    private fun get(url: String): Pair<String, String>? {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", userAgent)
            return when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) } to connection.url.toString()
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> error("spotify: HTTP $code")
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val BASE = "https://open.spotify.com"
        const val TRACK_URI = "spotify:track:"
        /** How many tracks the embed page lists, at most. */
        const val EMBED_LIMIT = 100
        const val TIMEOUT_MS = 15_000
        const val NBSP = ' '
        /** The kinds of link that hold a list of songs. */
        val LISTS = setOf("playlist", "album")
        val SPOTIFY = Regex("""(open\.spotify\.com/|spotify:(playlist|album|track):|spotify\.link/|spotify\.app\.link/)""")
        val PAGE = Regex("""open\.spotify\.com/(?:intl-[a-z-]+/)?(playlist|album|track|episode|show|artist)/([A-Za-z0-9]+)""")
        val URI = Regex("""spotify:(playlist|album|track):([A-Za-z0-9]+)""")
        val SHORT = Regex("""https?://(?:spotify\.link|spotify\.app\.link)/\S+""")
        val NEXT_DATA = Regex("""<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
        val SONG_COUNT = Regex("""<meta[^>]+name="music:song_count"[^>]+content="(\d+)"""")
    }
}
