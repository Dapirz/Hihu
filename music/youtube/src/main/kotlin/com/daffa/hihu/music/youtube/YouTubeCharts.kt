package com.daffa.hihu.music.youtube

import com.daffa.hihu.music.Song
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * YouTube Charts' trending music videos, asked for directly. NewPipeExtractor 0.27.1 has the
 * same request, but sends the country in upper case, which YouTube Charts now answers with 403
 * "The caller does not have permission", so its list comes back empty.
 */
internal object YouTubeCharts {

    /** The trending songs for [country] (ISO 3166, any case), in chart order. Throws on failure. */
    fun trending(country: String, language: String): List<Song> {
        val code = country.filter(Char::isLetter).lowercase(Locale.ROOT)
        val hl = language.filter(Char::isLetter).ifEmpty { "en" }
        // Every value here is letters only, so it can go into the JSON as it is.
        val body = """{"context":{"client":{"clientName":"$CLIENT_NAME","clientVersion":"$CLIENT_VERSION",""" +
            """"hl":"$hl","gl":"${code.uppercase(Locale.ROOT)}"}},"browseId":"$BROWSE_ID",""" +
            """"query":"perspective=CHART_DETAILS&chart_params_country_code=$code&chart_params_chart_type=TRENDING_VIDEOS"}"""
        return parse(post(body))
    }

    private fun post(body: String): String {
        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
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
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) error("youtube: charts answered HTTP $code")
            return connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(text: String): List<Song> {
        val views = JsonParser.`object`().from(text)
            .getObject("contents").getObject("sectionListRenderer").getArray("contents").getObject(0)
            .getObject("musicAnalyticsSectionRenderer").getObject("content")
            .getArray("videos").getObject(0).getArray("videoViews")
        return views.filterIsInstance<JsonObject>().mapNotNull(::toSong)
    }

    private fun toSong(entry: JsonObject): Song? {
        val id = entry.getString("id", null)?.takeIf { it.isNotBlank() } ?: return null
        if (!entry.getBoolean("isAvailable", true)) return null
        val artists = entry.getArray("artists").filterIsInstance<JsonObject>()
            .mapNotNull { it.getString("name", null) }
            .joinToString(", ")
        return Song(
            id = "yt:$id",
            title = entry.getString("title", "").orEmpty(),
            artist = artists.ifEmpty { entry.getString("channelName", null)?.removeSuffix(" - Topic") },
            album = null,
            durationMs = (entry.get("videoDuration")?.toString()?.toLongOrNull() ?: 0) * 1000,
            uri = "hihu-yt://$id",
            artworkUri = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
        )
    }

    private const val ENDPOINT = "https://charts.youtube.com/youtubei/v1/browse?alt=json&prettyPrint=false"
    private const val ORIGIN = "https://charts.youtube.com"
    private const val BROWSE_ID = "FEmusic_analytics_charts_home"
    private const val CLIENT_NAME = "WEB_MUSIC_ANALYTICS"
    private const val CLIENT_ID = "31"
    private const val CLIENT_VERSION = "2.0"
    private const val TIMEOUT_MS = 15_000
}
