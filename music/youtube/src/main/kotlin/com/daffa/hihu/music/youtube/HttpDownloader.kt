package com.daffa.hihu.music.youtube

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.net.HttpURLConnection
import java.net.URL

/**
 * The HTTP client NewPipeExtractor runs its requests through, on the platform's own
 * HttpURLConnection so no extra HTTP library ships in the app.
 */
internal class HttpDownloader : Downloader() {

    override fun execute(request: Request): Response {
        val connection = URL(request.url()).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = request.httpMethod()
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            request.headers().forEach { (name, values) ->
                values.forEachIndexed { i, value ->
                    if (i == 0) connection.setRequestProperty(name, value) else connection.addRequestProperty(name, value)
                }
            }
            request.dataToSend()?.let { body ->
                connection.doOutput = true
                connection.outputStream.use { it.write(body) }
            }

            val code = connection.responseCode
            if (code == HTTP_TOO_MANY_REQUESTS) {
                throw ReCaptchaException("youtube: rate limited", request.url())
            }
            val stream = if (code >= HttpURLConnection.HTTP_BAD_REQUEST) connection.errorStream else connection.inputStream
            val body = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            val headers = connection.headerFields.filterKeys { it != null }
            return Response(code, connection.responseMessage, headers, body, connection.url.toString())
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        /** The desktop browser NewPipe itself presents as; web stream URLs expect the same. */
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
        private const val TIMEOUT_MS = 20_000
        private const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
