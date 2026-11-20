package app.hihu.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import android.graphics.Color as AndroidColor

/**
 * The colours of surfaces that take their tint from the cover: the mini player and the full
 * player. They are always dark with light content, in both themes, like most music players.
 */
@Immutable
data class CoverColors(
    /** The cover's colour, darkened enough for light text on it. */
    val tint: Color,
    /** The near-black the full player's gradient starts from. */
    val base: Color = Base,
    val content: Color = Content,
    val contentMuted: Color = ContentMuted,
) {
    companion object {
        private val Base = Color(0xFF0B0B0D)
        private val Content = Color(0xFFF4F4F6)
        private val ContentMuted = Color(0xB3F4F4F6)

        /** Used while a cover is still loading or when the song has none. */
        val Neutral = CoverColors(tint = Color(0xFF26262B))

        /** Builds the colours from a cover's dominant colour, as returned by [dominantColor]. */
        fun from(argb: Int): CoverColors {
            val hsv = FloatArray(3)
            AndroidColor.colorToHSV(argb, hsv)
            hsv[1] = hsv[1].coerceAtMost(MAX_SATURATION)
            hsv[2] = TINT_VALUE
            return CoverColors(tint = Color(AndroidColor.HSVToColor(hsv)))
        }

        private const val MAX_SATURATION = 0.75f
        private const val TINT_VALUE = 0.30f
    }
}

/**
 * The most colourful average of [bitmap]: pixels are weighted by saturation and brightness, so
 * a black border or a grey sky does not wash out the colour that defines the cover. Reads a
 * 12×12 copy, so it is cheap enough to run once per song.
 */
fun dominantColor(bitmap: Bitmap): Int {
    val source = when (bitmap.config == Bitmap.Config.HARDWARE) {
        true -> bitmap.copy(Bitmap.Config.ARGB_8888, false)
        false -> bitmap
    }
    val small = Bitmap.createScaledBitmap(source, SAMPLE, SAMPLE, true)
    val hsv = FloatArray(3)
    var r = 0.0
    var g = 0.0
    var b = 0.0
    var weight = 0.0
    for (y in 0 until SAMPLE) {
        for (x in 0 until SAMPLE) {
            val pixel = small.getPixel(x, y)
            AndroidColor.colorToHSV(pixel, hsv)
            val w = hsv[1] * hsv[2] + FLOOR_WEIGHT
            r += AndroidColor.red(pixel) * w
            g += AndroidColor.green(pixel) * w
            b += AndroidColor.blue(pixel) * w
            weight += w
        }
    }
    return Color((r / weight / 255).toFloat(), (g / weight / 255).toFloat(), (b / weight / 255).toFloat()).toArgb()
}

private const val SAMPLE = 12
private const val FLOOR_WEIGHT = 0.02
