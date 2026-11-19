package com.daffa.hihu.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Material 3 with colours taken from the wallpaper on Android 12 and newer, and a fixed
 * fallback palette below that. [dark] forces light or dark; null follows the system.
 */
@Composable
fun HihuTheme(dark: Boolean? = null, content: @Composable () -> Unit) {
    val dark = dark ?: isSystemInDarkTheme()
    val context = LocalContext.current
    val colors: ColorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> FallbackDark
        else -> FallbackLight
    }
    ThemeSystemBars(dark)
    CompositionLocalProvider(LocalMetrics provides Metrics(), LocalDarkTheme provides dark) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}

/** Shortcuts to the theme values views need, mirroring [MaterialTheme]. */
object HihuTheme {
    val metrics: Metrics
        @Composable @ReadOnlyComposable get() = LocalMetrics.current
}

private val Seed = Color(0xFF7C5CFF)

private val FallbackLight = lightColorScheme(primary = Seed)

private val FallbackDark = darkColorScheme(primary = Color(0xFFC8BCFF))
