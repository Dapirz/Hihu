package app.hihu.music.youtube

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reads a video's loudness from YouTube's player answer. NewPipeExtractor reads the same answer
 * but does not expose it, and of the clients tried only YouTube's iOS app is sent the loudness
 * without signing in, so this asks as that app. No account and no visitor id are sent.
 */
internal object YouTubeLoudness {

    /** How many dB [videoId] is above YouTube's target (−14 LUFS), or null when not given. */
    fun loudnessDb(videoId: String): Double? {
        val body = JsonWriter.string().`object`()
            .`object`("context").`object`("client")
            .value("clientName", CLIENT_NAME)
            .value("clientVersion", CLIENT_VERSION)
            .value("deviceMake", "Apple")
            .value("deviceModel", DEVICE_MODEL)
            .value("osName", "iOS")
            .value("osVersion", OS_VERSION)
            .value("hl", "en")
            .end().end()
            .value("videoId", videoId)
            .end().done()
        val json = JsonParser.`object`().from(post(body))
        // Each audio format carries its own figure; the track's overall one is the fallback.
        json.getObject("streamingData").getArray("adaptiveFormats").filterIsInstance<JsonObject>()
            .firstOrNull { it.getString("mimeType", "").startsWith("audio/") && it.has("loudnessDb") }
            ?.let { return it.getDouble("loudnessDb") }
        val audio = json.getObject("playerConfig").getObject("audioConfig")
        if (!audio.has("perceptualLoudnessDb")) return null
        return audio.getDouble("perceptualLoudnessDb") - audio.getDouble("loudnessTargetLkfs", TARGET_LKFS)
    }

    private fun post(body: String): String {
        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("X-YouTube-Client-Name", CLIENT_ID)
            connection.setRequestProperty("X-YouTube-Client-Version", CLIENT_VERSION)
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) error("youtube: loudness answered HTTP $code")
            return connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            connection.disconnect()
        }
    }

    private const val ENDPOINT = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
    private const val CLIENT_NAME = "IOS"
    private const val CLIENT_ID = "5"
    // YouTube retires old iOS versions now and then; bump these if the loudness stops coming.
    private const val CLIENT_VERSION = "20.10.4"
    private const val DEVICE_MODEL = "iPhone16,2"
    private const val OS_VERSION = "18.3.2.22D82"
    private const val USER_AGENT = "com.google.ios.youtube/$CLIENT_VERSION ($DEVICE_MODEL; U; CPU iOS 18_3_2 like Mac OS X;)"
    private const val TARGET_LKFS = -14.0
    private const val TIMEOUT_MS = 10_000
}
