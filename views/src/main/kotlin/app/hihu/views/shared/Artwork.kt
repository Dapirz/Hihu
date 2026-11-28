package app.hihu.views.shared

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.net.HttpURLConnection
import java.net.URL
import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import app.hihu.ui.CoverColors
import app.hihu.ui.HihuTheme
import app.hihu.ui.Icons
import app.hihu.ui.dominantColor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * A square cover for [uri], or a music-note placeholder while it loads or when the file has
 * none. Covers are read with the system thumbnailer and kept in a small memory cache.
 */
@Composable
fun Artwork(uri: String?, size: Dp, modifier: Modifier = Modifier, radius: Dp = HihuTheme.metrics.radiusSm) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    // produceState keeps its last value when the keys change, so the cover of the previous song
    // is cleared here first; otherwise a reused row or mini player would keep showing it.
    val bitmap by produceState(initialValue = uri?.let { Covers.cached(it, px) }, uri, px) {
        value = uri?.let { Covers.cached(it, px) }
        if (uri != null && value == null) value = Covers.load(context, uri, px)?.asImageBitmap()
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        when (val image = bitmap) {
            null -> Icon(
                painter = painterResource(Icons.music),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size / 2),
            )
            else -> Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The tinted colours for the surfaces around the cover of [uri]. Starts neutral and fades to the
 * cover's colour once it is read; the colour is worked out once per cover and cached.
 */
@Composable
fun rememberCoverColors(uri: String?): CoverColors {
    val context = LocalContext.current
    val argb by produceState(initialValue = uri?.let(Covers::cachedColor), uri) {
        // Cleared first: produceState keeps the last cover's colour when the song changes.
        value = uri?.let(Covers::cachedColor)
        value = uri?.let { Covers.color(context, it) }
    }
    val target = remember(argb) { argb?.let(CoverColors::from) ?: CoverColors.Neutral }
    val tint by animateColorAsState(target.tint, label = "cover tint")
    return target.copy(tint = tint)
}

/**
 * The process-wide cover cache, bounded by bytes so a long scroll of search results cannot grow
 * it without limit. A miss is remembered too, so files without art are not re-read, but an image
 * URL that could not be fetched is tried again next time, since that is usually the network.
 * Used from the main thread only; one cover asked for by several rows at once is fetched once.
 */
private object Covers {
    private const val MAX_BYTES = 16 * 1024 * 1024
    private const val MISS_BYTES = 64
    private const val MAX_COLORS = 500
    private const val COLOR_PX = 96
    private val bitmaps = object : LruCache<String, Entry>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Entry) = value.bitmap?.allocationByteCount ?: MISS_BYTES
    }
    private val colors = LruCache<String, Tint>(MAX_COLORS)

    /**
     * The bytes of the image URLs fetched lately, so one cover shown at several sizes, and read for
     * its colour, is downloaded once.
     */
    private val fetched = object : LruCache<String, ByteArray>(MAX_FETCHED_BYTES) {
        override fun sizeOf(key: String, value: ByteArray) = value.size
    }

    private val loading = HashMap<String, CompletableDeferred<Bitmap?>>()

    private class Entry(val bitmap: Bitmap?)
    private class Tint(val argb: Int?)

    fun cached(uri: String, px: Int): ImageBitmap? = bitmaps.get(key(uri, px))?.bitmap?.asImageBitmap()

    fun cachedColor(uri: String): Int? = colors.get(uri)?.argb

    suspend fun load(context: Context, uri: String, px: Int): Bitmap? {
        val key = key(uri, px)
        bitmaps.get(key)?.let { return it.bitmap }
        loading[key]?.let { other ->
            // Nothing back may mean the row that started it went away; then this one loads it.
            other.await()?.let { return it }
            bitmaps.get(key)?.let { return it.bitmap }
        }
        val task = CompletableDeferred<Bitmap?>()
        loading[key] = task
        try {
            val remote = uri.startsWith("http")
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    when (remote) {
                        true -> download(uri, px)
                        false -> context.contentResolver.loadThumbnail(Uri.parse(uri), Size(px, px), null)
                    }
                }
            }
            // A file without art is remembered; an image URL that gave nothing is tried again later.
            if (!remote || result.getOrNull() != null) bitmaps.put(key, Entry(result.getOrNull()))
            task.complete(result.getOrNull())
            return result.getOrNull()
        } finally {
            loading.remove(key)
            // A cancelled load leaves the others waiting on it with nothing, to try again later.
            task.complete(null)
        }
    }

    suspend fun color(context: Context, uri: String): Int? {
        colors.get(uri)?.let { return it.argb }
        val bitmap = load(context, uri, COLOR_PX)
        val argb = bitmap?.let { withContext(Dispatchers.Default) { dominantColor(it) } }
        // Not kept when the cover could not be fetched, so the colour comes once it can be.
        if (bitmaps.get(key(uri, COLOR_PX)) != null) colors.put(uri, Tint(argb))
        return argb
    }

    private fun key(uri: String, px: Int) = "$uri@$px"

    /** Fetches an image URL and decodes it no larger than needed for [px]. */
    private fun download(url: String, px: Int): Bitmap? {
        val bytes = fetched.get(url) ?: fetch(url).also { fetched.put(url, it) }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= px && bounds.outHeight / (sample * 2) >= px) sample *= 2
        // A hardware bitmap lives only in GPU memory, instead of once in the heap and again as a
        // texture. dominantColor copies it back when it needs the pixels.
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.HARDWARE
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    /** The bytes at [url]; an answer other than success is an error, not an image. */
    private fun fetch(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            val code = connection.responseCode
            if (code !in HTTP_SUCCESS) throw IOException("cover: HTTP $code")
            return connection.inputStream.use(::readCapped)
        } finally {
            connection.disconnect()
        }
    }

    /** Reads [input] whole, refusing anything larger than a cover could reasonably be. */
    private fun readCapped(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return out.toByteArray()
            out.write(buffer, 0, read)
            if (out.size() > MAX_IMAGE_BYTES) throw IOException("cover: image too large")
        }
    }

    private const val TIMEOUT_MS = 15_000
    private const val BUFFER_BYTES = 16 * 1024
    private const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
    private const val MAX_FETCHED_BYTES = 4 * 1024 * 1024
    private val HTTP_SUCCESS = 200..299
}
