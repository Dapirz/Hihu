package app.hihu.music.youtube

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonStringWriter
import com.grack.nanojson.JsonWriter
import app.hihu.music.SessionExpired
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * A small client for YouTube Music's own web API, for the pages NewPipeExtractor does not have:
 * home, explore, moods and lyrics. It sends no account. YouTube hands out an anonymous visitor id on
 * the first answer; it is kept in memory only, for this run of the app, because later pages of
 * home come back empty without it.
 */
internal class InnerTube(
    private val language: String,
    private val country: String,
    /** The signed-in session's cookies, or null for an anonymous client. Read on every call. */
    private val cookies: () -> String? = { null },
) {
    @Volatile
    private var visitor: String? = null

    /** POSTs to `browse` with a [browseId] and [params], or a [continuation] for a next page. */
    fun browse(browseId: String? = null, params: String? = null, continuation: String? = null): JsonObject {
        val token = continuation?.let { URLEncoder.encode(it, "UTF-8") }
        val query = token?.let { "&ctoken=$it&continuation=$it&type=next" }.orEmpty()
        return call("browse", query) {
            browseId?.let { value("browseId", it) }
            params?.let { value("params", it) }
        }
    }

    /**
     * Browse page [browseId] as YouTube Music's Android app asks for it, always without an
     * account. A song's lyrics page answers the app with timed lines that the web player is not
     * given.
     */
    fun browseAsApp(browseId: String): JsonObject = call("browse", "", Client.App) { value("browseId", browseId) }

    /** The next page of a list whose token came in a `continuationCommand`, sent in the body. */
    fun browseContinuation(token: String): JsonObject = call("browse", "") { value("continuation", token) }

    /** Likes [videoId] on the signed-in account, or removes the like, as YouTube Music's like button does. */
    fun like(videoId: String, liked: Boolean) {
        val endpoint = when (liked) {
            true -> "like/like"
            false -> "like/removelike"
        }
        call(endpoint, "") { `object`("target").value("videoId", videoId).end() }
    }

    /** Dislikes [videoId] on the signed-in account, or removes the dislike, as YouTube Music's button does. */
    fun dislike(videoId: String, disliked: Boolean) {
        val endpoint = when (disliked) {
            true -> "like/dislike"
            false -> "like/removelike"
        }
        call(endpoint, "") { `object`("target").value("videoId", videoId).end() }
    }

    /** POSTs to `account/account_menu`, which names the signed-in account. */
    fun accountMenu(): JsonObject = call("account/account_menu", "") {}

    /** POSTs to `next`, the watch page of [videoId], whose tabs lead to its lyrics. */
    fun next(videoId: String): JsonObject = call("next", "") { value("videoId", videoId) }

    /** The play queue of [playlistId], as YouTube Music queues a mix; [continuation] asks for the page after. */
    fun queue(playlistId: String, continuation: String? = null): JsonObject = call("next", "") {
        when (continuation) {
            null -> value("playlistId", playlistId)
            else -> value("continuation", continuation)
        }
    }

    /** Who a request says it comes from: YouTube Music's web player, or its Android app. */
    private enum class Client(val clientName: String, val id: String, val version: String, val userAgent: String) {
        Web(CLIENT_NAME, CLIENT_ID, CLIENT_VERSION, HttpDownloader.USER_AGENT),
        App(APP_CLIENT_NAME, APP_CLIENT_ID, APP_CLIENT_VERSION, APP_USER_AGENT),
    }

    private fun call(
        endpoint: String,
        query: String,
        client: Client = Client.Web,
        fields: JsonStringWriter.() -> Unit,
    ): JsonObject {
        val body = JsonWriter.string().`object`()
            .`object`("context").`object`("client")
            .value("clientName", client.clientName)
            .value("clientVersion", client.version)
            .value("hl", language)
            .value("gl", country)
            .apply { visitor?.let { value("visitorData", it) } }
            .end().end()
            .apply(fields)
            .end().done()
        val json = JsonParser.`object`().from(post("$BASE$endpoint?prettyPrint=false$query", body, client))
        json.getObject("responseContext").getString("visitorData", null)?.let { visitor = it }
        return json
    }

    private fun post(url: String, body: String, client: Client): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("User-Agent", client.userAgent)
            connection.setRequestProperty("X-YouTube-Client-Name", client.id)
            connection.setRequestProperty("X-YouTube-Client-Version", client.version)
            visitor?.let { connection.setRequestProperty("X-Goog-Visitor-Id", it) }
            // Only the web player speaks for the page and the account; the app's requests carry neither.
            if (client == Client.Web) {
                connection.setRequestProperty("Origin", ORIGIN)
                connection.setRequestProperty("Referer", "$ORIGIN/")
            }
            cookies()?.takeIf { client == Client.Web }?.let { jar ->
                connection.setRequestProperty("Cookie", jar)
                connection.setRequestProperty("X-Goog-AuthUser", "0")
                connection.setRequestProperty("X-Origin", ORIGIN)
                authorization(jar)?.let { connection.setRequestProperty("Authorization", it) }
            }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_UNAUTHORIZED && cookies() != null) throw SessionExpired("youtube: session refused")
            if (code != HttpURLConnection.HTTP_OK) error("youtube: music API answered HTTP $code")
            return connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * The `SAPISIDHASH` header YouTube's web client signs a request with: a SHA-1 of the time,
     * the SAPISID cookie and the origin. Null when the cookies have no SAPISID.
     */
    private fun authorization(jar: String): String? {
        val sapisid = cookie(jar, "SAPISID") ?: cookie(jar, "__Secure-3PAPISID") ?: return null
        val now = System.currentTimeMillis() / 1000
        val digest = MessageDigest.getInstance("SHA-1").digest("$now $sapisid $ORIGIN".toByteArray(Charsets.UTF_8))
        return "SAPISIDHASH ${now}_" + digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        /** The value of cookie [name] in a `Cookie` header, or null. */
        fun cookie(jar: String, name: String): String? = jar.split(';').map { it.trim() }
            .firstOrNull { it.startsWith("$name=") }?.substringAfter('=')?.takeIf { it.isNotEmpty() }

        private const val BASE = "https://music.youtube.com/youtubei/v1/"
        private const val ORIGIN = "https://music.youtube.com"
        private const val CLIENT_NAME = "WEB_REMIX"
        private const val CLIENT_ID = "67"
        // YouTube accepts older web client versions for a long time; bump it if pages turn empty.
        private const val CLIENT_VERSION = "1.20251110.01.00"
        private const val TIMEOUT_MS = 15_000
        private const val APP_CLIENT_NAME = "ANDROID_MUSIC"
        private const val APP_CLIENT_ID = "21"
        private const val APP_CLIENT_VERSION = "7.27.52"
        private const val APP_USER_AGENT = "com.google.android.apps.youtube.music/$APP_CLIENT_VERSION (Linux; U; Android 14) gzip"
    }
}
