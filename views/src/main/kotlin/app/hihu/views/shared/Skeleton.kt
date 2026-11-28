package app.hihu.views.shared

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import app.hihu.ui.HihuTheme
import app.hihu.views.R

/**
 * Grey shapes where song rows will be, pulsing gently, shown while a list loads for the first
 * time so the screen keeps its layout instead of a spinner on an empty page.
 */
@Composable
fun SkeletonRows(count: Int = SKELETON_ROWS, modifier: Modifier = Modifier) {
    val m = HihuTheme.metrics
    Column(modifier.fillMaxWidth().pulse()) {
        repeat(count) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = m.gapLg, vertical = m.gapMd),
            ) {
                Bone(Modifier.size(m.rowArtwork), m.radiusMd)
                Column(Modifier.weight(1f).padding(start = m.gapLg), verticalArrangement = Arrangement.spacedBy(m.gapSm)) {
                    Bone(Modifier.fillMaxWidth(TITLE_SHARE).height(m.gapLg), m.radiusSm)
                    Bone(Modifier.fillMaxWidth(SUBTITLE_SHARE).height(m.gapMd), m.radiusSm)
                }
            }
        }
    }
}

/** Grey shapes where shelves of cards will be: a heading and a row of covers, [count] times. */
@Composable
fun SkeletonShelves(count: Int = SKELETON_SHELVES, modifier: Modifier = Modifier) {
    val m = HihuTheme.metrics
    Column(modifier.fillMaxWidth().pulse()) {
        repeat(count) {
            Bone(Modifier.padding(horizontal = m.gapXl, vertical = m.gapSm).width(m.cardWidth).height(m.gapXl), m.radiusSm)
            Row(
                horizontalArrangement = Arrangement.spacedBy(m.gapMd),
                modifier = Modifier.padding(start = m.gapLg, end = m.gapLg, bottom = m.gapLg),
            ) {
                repeat(SHELF_CARDS) {
                    Column(verticalArrangement = Arrangement.spacedBy(m.gapSm)) {
                        Bone(Modifier.size(m.cardWidth), m.radiusMd)
                        Bone(Modifier.width(m.cardWidth * TITLE_SHARE).height(m.gapLg), m.radiusSm)
                    }
                }
            }
        }
    }
}

@Composable
private fun Bone(modifier: Modifier, radius: Dp) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(radius)))
}

/** Fades the shapes in and out, and names them for a screen reader as loading. */
@Composable
private fun Modifier.pulse(): Modifier {
    val label = stringResource(R.string.loading)
    val alpha by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = PULSE_LOW,
        targetValue = PULSE_HIGH,
        animationSpec = infiniteRepeatable(tween(PULSE_MS), RepeatMode.Reverse),
        label = "skeleton alpha",
    )
    return semantics { contentDescription = label }.alpha(alpha)
}

private const val SKELETON_ROWS = 8
private const val SKELETON_SHELVES = 2
private const val SHELF_CARDS = 3
private const val TITLE_SHARE = 0.7f
private const val SUBTITLE_SHARE = 0.45f
private const val PULSE_LOW = 0.4f
private const val PULSE_HIGH = 0.9f
private const val PULSE_MS = 800
