package com.daffa.hihu.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Whether the theme in use is dark, so screens can put the system bars back after a change. */
internal val LocalDarkTheme = staticCompositionLocalOf { false }

/** Gives the status and navigation bar icons the contrast the theme needs. */
@Composable
internal fun ThemeSystemBars(dark: Boolean) {
    val view = LocalView.current
    LaunchedEffect(view, dark) { view.setLightBars(!dark) }
}

/**
 * For a screen that is always dark whatever the theme, like the player: light bar icons while
 * it shows, and the theme's own again when it goes.
 */
@Composable
fun DarkSystemBars() {
    val view = LocalView.current
    val dark = LocalDarkTheme.current
    DisposableEffect(view, dark) {
        view.setLightBars(false)
        onDispose { view.setLightBars(!dark) }
    }
}

/** Dark icons on the bars when [light], for a light background behind them. */
private fun View.setLightBars(light: Boolean) {
    if (isInEditMode) return
    val window = context.activity()?.window ?: return
    WindowCompat.getInsetsController(window, this).apply {
        isAppearanceLightStatusBars = light
        isAppearanceLightNavigationBars = light
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
