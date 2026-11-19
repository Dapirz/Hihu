package com.daffa.hihu.views.shared

import com.daffa.hihu.state.Palette
import com.daffa.hihu.ui.ThemeColors
import com.daffa.hihu.views.R

/** The theme colours a saved [Palette] stands for; `state` and `ui` don't know each other. */
val Palette.colors: ThemeColors
    get() = when (this) {
        Palette.Wallpaper -> ThemeColors.Wallpaper
        Palette.Violet -> ThemeColors.Violet
        Palette.Blue -> ThemeColors.Blue
        Palette.Teal -> ThemeColors.Teal
        Palette.Green -> ThemeColors.Green
        Palette.Rose -> ThemeColors.Rose
        Palette.Amber -> ThemeColors.Amber
    }

/** The name a picker reads out for [Palette]. */
val Palette.label: Int
    get() = when (this) {
        Palette.Wallpaper -> R.string.palette_wallpaper
        Palette.Violet -> R.string.palette_violet
        Palette.Blue -> R.string.palette_blue
        Palette.Teal -> R.string.palette_teal
        Palette.Green -> R.string.palette_green
        Palette.Rose -> R.string.palette_rose
        Palette.Amber -> R.string.palette_amber
    }

/** The palettes this device can show; wallpaper colours need Android 12. */
val availablePalettes: List<Palette>
    get() = Palette.entries.filter { it.colors in ThemeColors.available }
