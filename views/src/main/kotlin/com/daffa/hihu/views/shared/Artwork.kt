package com.daffa.hihu.views.shared

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
import com.daffa.hihu.ui.CoverColors
import com.daffa.hihu.ui.HihuTheme
import com.daffa.hihu.ui.Icons
import com.daffa.hihu.ui.dominantColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A square cover for [uri], or a music-note placeholder while it loads or when the file has
 * none. Covers are read with the system thumbnailer and kept in a small memory cache.
 */
@Composable
fun Artwork(uri: String?, size: Dp, modifier: Modifier = Modifier, radius: Dp = HihuTheme.metrics.radiusSm) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState(initialValue = uri?.let { Covers.cached(it, px) }, uri, px) {
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
        value = uri?.let { Covers.color(context, it) }
    }
    val target = remember(argb) { argb?.let(CoverColors::from) ?: CoverColors.Neutral }
    val tint by animateColorAsState(target.tint, label = "cover tint")
    return target.copy(tint = tint)
}

/**
 * The process-wide cover cache, bounded by bytes so a long scroll of search results cannot grow
 * it without limit. A miss is remembered too, so files without art are not re-read.
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

    private class Entry(val bitmap: Bitmap?)
    private class Tint(val argb: Int?)

    fun cached(uri: String, px: Int): ImageBitmap? = bitmaps.get(key(uri, px))?.bitmap?.asImageBitmap()

    fun cachedColor(uri: String): Int? = colors.get(uri)?.argb

    suspend fun load(context: Context, uri: String, px: Int): Bitmap? {
        bitmaps.get(key(uri, px))?.let { return it.bitmap }
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                when (uri.startsWith("http")) {
                    true -> download(uri, px)
                    false -> context.contentResolver.loadThumbnail(Uri.parse(uri), Size(px, px), null)
                }
            }.getOrNull()
        }
        bitmaps.put(key(uri, px), Entry(bitmap))
        return bitmap
    }

    suspend fun color(context: Context, uri: String): Int? {
        colors.get(uri)?.let { return it.argb }
        val bitmap = load(context, uri, COLOR_PX)
        val argb = bitmap?.let { withContext(Dispatchers.Default) { dominantColor(it) } }
        colors.put(uri, Tint(argb))
        return argb
    }

    private fun key(uri: String, px: Int) = "$uri@$px"

    /** Fetches an image URL and decodes it no larger than needed for [px]. */
    private fun download(url: String, px: Int): Bitmap? {
        val connection = URL(url).openConnection() as HttpURLConnection
        val bytes = try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
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

    private const val TIMEOUT_MS = 15_000
}
