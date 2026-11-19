package com.daffa.hihu.music.youtube

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonStringWriter
import com.grack.nanojson.JsonWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * A small client for YouTube Music's own web API, for the pages NewPipeExtractor does not have:
 * home, explore, moods and lyrics. It sends no account. YouTube hands out an anonymous visitor id on
 * the first answer; it is kept in memory only, for this run of the app, because later pages of
 * home come back empty without it.
 */
internal class InnerTube(private val language: String, private val country: String) {
    @Volatile
    private var visitor: String? = null

    /** POSTs to `browse` with a [browseId] and [params], or a [continuation] for a next page. */
    fun browse(browseId: String? = null, params: String? = null, continuation: String? = null): JsonObject {
        val query = continuation?.let { "&ctoken=$it&continuation=$it&type=next" }.orEmpty()
        return call("browse", query) {
            browseId?.let { value("browseId", it) }
            params?.let { value("params", it) }
        }
    }

    /** POSTs to `next`, the watch page of [videoId], whose tabs lead to its lyrics. */
    fun next(videoId: String): JsonObject = call("next", "") { value("videoId", videoId) }

    private fun call(endpoint: String, query: String, fields: JsonStringWriter.() -> Unit): JsonObject {
        val body = JsonWriter.string().`object`()
            .`object`("context").`object`("client")
            .value("clientName", CLIENT_NAME)
            .value("clientVersion", CLIENT_VERSION)
            .value("hl", language)
            .value("gl", country)
            .apply { visitor?.let { value("visitorData", it) } }
            .end().end()
            .apply(fields)
            .end().done()
        val json = JsonParser.`object`().from(post("$BASE$endpoint?prettyPrint=false$query", body))
        json.getObject("responseContext").getString("visitorData", null)?.let { visitor = it }
        return json
    }

    private fun post(url: String, body: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Origin", ORIGIN)
            connection.setRequestProperty("Referer", "$ORIGIN/")
            connection.setRequestProperty("User-Agent", HttpDownloader.USER_AGENT)
            connection.setRequestProperty("X-YouTube-Client-Name", CLIENT_ID)
            connection.setRequestProperty("X-YouTube-Client-Version", CLIENT_VERSION)
            visitor?.let { connection.setRequestProperty("X-Goog-Visitor-Id", it) }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) error("youtube: music API answered HTTP $code")
            return connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val BASE = "https://music.youtube.com/youtubei/v1/"
        const val ORIGIN = "https://music.youtube.com"
        const val CLIENT_NAME = "WEB_REMIX"
        const val CLIENT_ID = "67"
        // YouTube accepts older web client versions for a long time; bump it if pages turn empty.
        const val CLIENT_VERSION = "1.20251110.01.00"
        const val TIMEOUT_MS = 15_000
    }
}
