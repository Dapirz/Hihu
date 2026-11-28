package app.hihu.ui

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
    /** The seek bar's track while its thumb is held. */
    val seekTrackActive: Dp = 8.dp,
    val seekThumb: Dp = 16.dp,
    val spinnerStroke: Dp = 2.dp,
    val miniProgress: Dp = 2.dp,
    /** How far the player must be dragged for a swipe to count: down to close, sideways to skip. */
    val swipeDistance: Dp = 96.dp,
    /** The tallest a list inside a dialog grows before it scrolls. */
    val dialogListMax: Dp = 360.dp,
    /** The widest a column of settings grows on a tablet or a phone on its side. */
    val contentMaxWidth: Dp = 600.dp,
    /** The narrowest window the player splits into cover and controls side by side. */
    val twoColumnMin: Dp = 560.dp,
    /** The space above and below a tab's big title. */
    val titleGap: Dp = 16.dp,
    /** True in a short window, such as a pop-up over a game: the sizes below shrink to fit. */
    val compact: Boolean = false,
    val none: Dp = 0.dp,
    /** How close to a list edge a dragged row starts it scrolling, and how far per frame. */
    val reorderEdge: Dp = 72.dp,
    val reorderStep: Dp = 12.dp,
    val rankWidth: Dp = 36.dp,
    val cardWidth: Dp = 148.dp,
    val songRowHeight: Dp = 76.dp,
    val moodWidth: Dp = 168.dp,
    val moodHeight: Dp = 52.dp,
    val moodStripe: Dp = 6.dp,
    /** How much of the screen's width a column of a song shelf takes; the next one peeks in. */
    val shelfColumnShare: Float = 0.86f,
    /** The play icon's size as a share of its round button. */
    val playIconShare: Float = 0.7f,
    /** How strongly the unplayed part of the player's seek bar shows. */
    val seekTrackAlpha: Float = 0.35f,
    /** A colour circle in the theme picker. */
    val swatch: Dp = 44.dp,
    val swatchRing: Dp = 3.dp,
)

internal val LocalMetrics = staticCompositionLocalOf { Metrics() }

/**
 * The sizes for a window too short for the usual ones, like Samsung's pop-up view over a game:
 * a slimmer mini player, a smaller play button and less room around titles.
 */
val CompactMetrics = Metrics(
    miniHeight = 52.dp,
    miniArtwork = 36.dp,
    playButton = 56.dp,
    titleGap = 4.dp,
    compact = true,
)

/** Below this height a window gets [CompactMetrics]. */
val COMPACT_HEIGHT = 480.dp

/** How much smaller everything is drawn in such a window, as a share of the usual size. */
const val COMPACT_SCALE = 0.8f
