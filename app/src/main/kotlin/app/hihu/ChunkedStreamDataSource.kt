package app.hihu

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource

/**
 * Reads a YouTube stream (`*.googlevideo.com/videoplayback`) as a series of 1 MB requests, each
 * naming its bytes with the `range` URL parameter, the way YouTube's own clients and NewPipe do.
 * YouTube slows a single request for the whole file to about real time: a 6 MB song took three
 * minutes that way and one second in chunks. Any other URL is read as it is.
 */
@OptIn(UnstableApi::class)
class ChunkedStreamDataSource private constructor(private val http: HttpDataSource.Factory) : BaseDataSource(true) {

    class Factory(private val http: HttpDataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = ChunkedStreamDataSource(http)
    }

    private var spec: DataSpec? = null
    private var current: DataSource? = null
    private var chunked = false
    private var opened = false
    private var position = 0L
    private var end = 0L
    private var requests = 0

    override fun open(dataSpec: DataSpec): Long {
        spec = dataSpec
        transferInitializing(dataSpec)
        val total = streamLength(dataSpec.uri)
        chunked = total != null
        val length = when (total) {
            null -> http.createDataSource().also { current = it }.open(dataSpec)
            else -> {
                position = dataSpec.position
                end = if (dataSpec.length != C.LENGTH_UNSET.toLong()) position + dataSpec.length else total
                openChunk()
                end - position
            }
        }
        opened = true
        transferStarted(dataSpec)
        return length
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val source = current ?: return C.RESULT_END_OF_INPUT
        if (!chunked) return source.read(buffer, offset, length).also { if (it > 0) bytesTransferred(it) }
        if (position >= end) return C.RESULT_END_OF_INPUT
        var read = source.read(buffer, offset, length)
        if (read == C.RESULT_END_OF_INPUT) {
            source.close()
            openChunk()
            read = current?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT
        }
        if (read > 0) {
            position += read
            bytesTransferred(read)
        }
        return read
    }

    override fun getUri(): Uri? = spec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = current?.responseHeaders.orEmpty()

    override fun close() {
        try {
            current?.close()
        } finally {
            current = null
            spec = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    /** Opens the next chunk, from [position] up to a megabyte or the end. */
    private fun openChunk() {
        val base = spec ?: return
        val last = minOf(position + CHUNK_BYTES, end) - 1
        val uri = base.uri.buildUpon()
            .appendQueryParameter("range", "$position-$last")
            .appendQueryParameter("rn", (++requests).toString())
            .build()
        val chunk = DataSpec.Builder()
            .setUri(uri)
            .setHttpRequestHeaders(base.httpRequestHeaders)
            // YouTube's web player asks for chunks with this tiny POST body; so does NewPipe.
            .setHttpMethod(DataSpec.HTTP_METHOD_POST)
            .setHttpBody(POST_BODY)
            .build()
        val source = http.createDataSource()
        try {
            source.open(chunk)
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            // Only the range and the client name: the URL itself carries the phone's IP address.
            Log.w(
                TAG,
                "stream: chunk $position-$last of $end (request $requests, client ${base.uri.getQueryParameter("c")}) " +
                    "refused with ${e.responseCode}",
            )
            throw e
        }
        current = source
    }

    private companion object {
        const val TAG = "Hihu"
        const val CHUNK_BYTES = 1L shl 20
        val POST_BODY = byteArrayOf(0x78, 0)

        /** The stream's length from its `clen` parameter, or null when [uri] is not a YouTube stream. */
        fun streamLength(uri: Uri): Long? {
            val host = uri.host ?: return null
            if (!host.endsWith(".googlevideo.com") || uri.path != "/videoplayback") return null
            return uri.getQueryParameter("clen")?.toLongOrNull()?.takeIf { it > 0 }
        }
    }
}
