package app.hihu.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * A Material 3 colour scheme grown from one [seed] colour, the way Material's "tonal spot"
 * builds one: palettes of the seed's hue at a few strengths, each read at the tones Material
 * assigns to every role. Tone here is CIE L*, which is what Material's own tone is, so text
 * keeps its contrast whatever the hue. About as close to Material's tool as a few lines get,
 * without its library.
 */
internal fun seededScheme(seed: Color, dark: Boolean): ColorScheme {
    val (hue, seedChroma) = seed.hueChroma()
    val primary = Tones(hue, seedChroma.coerceIn(MIN_PRIMARY_CHROMA, MAX_PRIMARY_CHROMA))
    val secondary = Tones(hue, SECONDARY_CHROMA)
    val tertiary = Tones(hue + TERTIARY_TURN, TERTIARY_CHROMA)
    val neutral = Tones(hue, NEUTRAL_CHROMA)
    val variant = Tones(hue, VARIANT_CHROMA)
    return when (dark) {
        true -> darkColorScheme(
            primary = primary(80), onPrimary = primary(20),
            primaryContainer = primary(30), onPrimaryContainer = primary(90), inversePrimary = primary(40),
            secondary = secondary(80), onSecondary = secondary(20),
            secondaryContainer = secondary(30), onSecondaryContainer = secondary(90),
            tertiary = tertiary(80), onTertiary = tertiary(20),
            tertiaryContainer = tertiary(30), onTertiaryContainer = tertiary(90),
            background = neutral(6), onBackground = neutral(90),
            surface = neutral(6), onSurface = neutral(90), surfaceTint = primary(80),
            surfaceVariant = variant(30), onSurfaceVariant = variant(80),
            inverseSurface = neutral(90), inverseOnSurface = neutral(20),
            outline = variant(60), outlineVariant = variant(30),
            surfaceBright = neutral(24), surfaceDim = neutral(6),
            surfaceContainerLowest = neutral(4), surfaceContainerLow = neutral(10),
            surfaceContainer = neutral(12), surfaceContainerHigh = neutral(17), surfaceContainerHighest = neutral(22),
        )
        false -> lightColorScheme(
            primary = primary(40), onPrimary = primary(100),
            primaryContainer = primary(90), onPrimaryContainer = primary(10), inversePrimary = primary(80),
            secondary = secondary(40), onSecondary = secondary(100),
            secondaryContainer = secondary(90), onSecondaryContainer = secondary(10),
            tertiary = tertiary(40), onTertiary = tertiary(100),
            tertiaryContainer = tertiary(90), onTertiaryContainer = tertiary(10),
            background = neutral(98), onBackground = neutral(10),
            surface = neutral(98), onSurface = neutral(10), surfaceTint = primary(40),
            surfaceVariant = variant(90), onSurfaceVariant = variant(30),
            inverseSurface = neutral(20), inverseOnSurface = neutral(95),
            outline = variant(50), outlineVariant = variant(80),
            surfaceBright = neutral(98), surfaceDim = neutral(87),
            surfaceContainerLowest = neutral(100), surfaceContainerLow = neutral(96),
            surfaceContainer = neutral(94), surfaceContainerHigh = neutral(92), surfaceContainerHighest = neutral(90),
        )
    }
}

/** One hue at one strength; calling it with a tone (0 black to 100 white) gives that colour. */
private class Tones(private val hue: Double, private val chroma: Double) {
    operator fun invoke(tone: Int): Color = lchToColor(tone.toDouble(), chroma, hue)
}

/** The seed's hue in degrees and its chroma, in CIE LCh. */
private fun Color.hueChroma(): Pair<Double, Double> {
    val x = xyz(red) * 0.4124 + xyz(green) * 0.3576 + xyz(blue) * 0.1805
    val y = xyz(red) * 0.2126 + xyz(green) * 0.7152 + xyz(blue) * 0.0722
    val z = xyz(red) * 0.0193 + xyz(green) * 0.1192 + xyz(blue) * 0.9505
    val fx = labF(x / WHITE_X)
    val fy = labF(y)
    val fz = labF(z / WHITE_Z)
    val a = 500 * (fx - fy)
    val b = 200 * (fy - fz)
    return Math.toDegrees(atan2(b, a)) to hypot(a, b)
}

/**
 * The sRGB colour at lightness [l], chroma [c] and hue [h]. A chroma the screen cannot show at
 * that lightness is lowered step by step until it can, so the hue and tone stay put.
 */
private fun lchToColor(l: Double, c: Double, h: Double): Color {
    var chroma = c
    while (true) {
        val rad = Math.toRadians(h)
        val rgb = labToLinearRgb(l, chroma * cos(rad), chroma * sin(rad))
        if (chroma <= 0.0 || rgb.all { it in -GAMUT_SLACK..1 + GAMUT_SLACK }) {
            return Color(gamma(rgb[0]), gamma(rgb[1]), gamma(rgb[2]))
        }
        chroma -= CHROMA_STEP
        if (chroma < 0.0) chroma = 0.0
    }
}

private fun labToLinearRgb(l: Double, a: Double, b: Double): DoubleArray {
    val fy = (l + 16) / 116
    val x = WHITE_X * labFInverse(fy + a / 500)
    val y = labFInverse(fy)
    val z = WHITE_Z * labFInverse(fy - b / 200)
    return doubleArrayOf(
        x * 3.2406 - y * 1.5372 - z * 0.4986,
        -x * 0.9689 + y * 1.8758 + z * 0.0415,
        x * 0.0557 - y * 0.2040 + z * 1.0570,
    )
}

private fun xyz(channel: Float): Double {
    val c = channel.toDouble()
    return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}

private fun gamma(linear: Double): Float {
    val c = linear.coerceIn(0.0, 1.0)
    return (if (c <= 0.0031308) c * 12.92 else 1.055 * c.pow(1 / 2.4) - 0.055).toFloat()
}

private fun labF(t: Double): Double = if (t > LAB_E) cbrt(t) else (LAB_K * t + 16) / 116

private fun labFInverse(f: Double): Double = if (f * f * f > LAB_E) f * f * f else (116 * f - 16) / LAB_K

private const val WHITE_X = 0.95047
private const val WHITE_Z = 1.08883
private const val LAB_E = 216.0 / 24389
private const val LAB_K = 24389.0 / 27
private const val GAMUT_SLACK = 0.001
private const val CHROMA_STEP = 1.0

private const val MIN_PRIMARY_CHROMA = 40.0
private const val MAX_PRIMARY_CHROMA = 70.0
private const val SECONDARY_CHROMA = 16.0
private const val TERTIARY_CHROMA = 28.0
private const val TERTIARY_TURN = 60.0
private const val NEUTRAL_CHROMA = 4.0
private const val VARIANT_CHROMA = 8.0
