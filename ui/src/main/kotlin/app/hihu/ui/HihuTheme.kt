package app.hihu.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * The colour families the theme can be built from: the wallpaper's on Android 12 and newer, or
 * one of a few fixed seeds.
 */
enum class ThemeColors(internal val seed: Color?) {
    Wallpaper(null),
    Violet(Color(0xFF7C5CFF)),
    Blue(Color(0xFF2F6FED)),
    Teal(Color(0xFF00897B)),
    Green(Color(0xFF3F8F3A)),
    Rose(Color(0xFFD9487A)),
    Amber(Color(0xFFE08A00)),
    ;

    /** The colour a picker shows for this family; for the wallpaper, its current primary. */
    @Composable
    fun swatch(): Color = seed ?: when (wallpaperAvailable) {
        true -> dynamicLightColorScheme(LocalContext.current).primary
        false -> Violet.seed!!
    }

    companion object {
        /** Wallpaper colours exist from Android 12; below that the picker leaves them out. */
        val wallpaperAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        /** The families this device can use, in picker order. */
        val available: List<ThemeColors> get() = entries.filter { it != Wallpaper || wallpaperAvailable }
    }
}

/**
 * Material 3 in the chosen [colors]. [dark] forces light or dark; null follows the system.
 * [black] turns the dark theme's background and surfaces pure black, which an AMOLED screen
 * shows by switching those pixels off.
 */
@Composable
fun HihuTheme(
    dark: Boolean? = null,
    colors: ThemeColors = ThemeColors.Wallpaper,
    black: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = dark ?: isSystemInDarkTheme()
    val context = LocalContext.current
    val wallpaper = colors.seed == null && ThemeColors.wallpaperAvailable
    val base: ColorScheme = when {
        wallpaper && dark -> dynamicDarkColorScheme(context)
        wallpaper -> dynamicLightColorScheme(context)
        else -> remember(colors, dark) { seededScheme(colors.seed ?: ThemeColors.Violet.seed!!, dark) }
    }
    val scheme = when (dark && black) {
        true -> remember(base) { base.blackened() }
        false -> base
    }
    ThemeSystemBars(dark)
    CompositionLocalProvider(LocalMetrics provides Metrics(), LocalDarkTheme provides dark) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Shortcuts to the theme values views need, mirroring [MaterialTheme]. */
object HihuTheme {
    val metrics: Metrics
        @Composable @ReadOnlyComposable get() = LocalMetrics.current
}

/**
 * Black background and the lowest surfaces, with the raised ones kept just above black so cards
 * and sheets still stand apart from the page.
 */
private fun ColorScheme.blackened(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF080808),
    surfaceContainer = Color(0xFF101010),
    surfaceContainerHigh = Color(0xFF181818),
    surfaceContainerHighest = Color(0xFF222222),
    surfaceBright = Color(0xFF2A2A2A),
)
