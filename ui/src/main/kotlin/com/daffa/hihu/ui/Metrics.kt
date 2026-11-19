package com.daffa.hihu.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Every size, gap and corner the views use. Views read these through [HihuTheme.metrics] and
 * never write a literal dp, so the whole app scales from one place.
 */
@Immutable
data class Metrics(
    val gapXs: Dp = 4.dp,
    val gapSm: Dp = 8.dp,
    val gapMd: Dp = 12.dp,
    val gapLg: Dp = 16.dp,
    val gapXl: Dp = 24.dp,
    val gapXxl: Dp = 32.dp,
    val rowArtwork: Dp = 52.dp,
    val miniArtwork: Dp = 48.dp,
    val miniHeight: Dp = 68.dp,
    val playerArtworkMax: Dp = 360.dp,
    val collectionArtwork: Dp = 200.dp,
    val radiusSm: Dp = 8.dp,
    val radiusMd: Dp = 12.dp,
    val radiusLg: Dp = 24.dp,
    val radiusSheet: Dp = 28.dp,
    val radiusPill: Dp = 34.dp,
    val iconSm: Dp = 18.dp,
    val iconMd: Dp = 24.dp,
    val iconLg: Dp = 32.dp,
    val iconXl: Dp = 40.dp,
    val roundButton: Dp = 48.dp,
    val playButton: Dp = 76.dp,
    val divider: Dp = 1.dp,
    val seekTrack: Dp = 4.dp,
    val seekThumb: Dp = 16.dp,
    val none: Dp = 0.dp,
    /** How close to a list edge a dragged row starts it scrolling, and how far per frame. */
    val reorderEdge: Dp = 72.dp,
    val reorderStep: Dp = 12.dp,
)

internal val LocalMetrics = staticCompositionLocalOf { Metrics() }
