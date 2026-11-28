package app.hihu.views.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import app.hihu.state.NowPlaying
import app.hihu.ui.HihuTheme
import app.hihu.ui.Icons
import app.hihu.views.R
import app.hihu.views.shared.Artwork
import app.hihu.views.shared.nowPlayingCover
import app.hihu.views.shared.rememberCoverColors

/**
 * The floating pill above the bottom edge while something is loaded, tinted by the cover, with a
 * thin line along its bottom for how far the song has played. Tapping it opens the full player;
 * swiping it left plays the next song and right the previous one.
 */
@Composable
fun MiniPlayer(
    now: NowPlaying,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val m = HihuTheme.metrics
    val colors = rememberCoverColors(now.artworkUri)
    val openLabel = stringResource(R.string.open_player)
    val buttons = IconButtonDefaults.iconButtonColors(
        contentColor = colors.content,
        disabledContentColor = colors.contentMuted,
    )
    val current by rememberUpdatedState(now)
    val next by rememberUpdatedState(onNext)
    val previous by rememberUpdatedState(onPrevious)
    var shift by remember { mutableFloatStateOf(0f) }
    Surface(
        color = colors.tint,
        contentColor = colors.content,
        shape = RoundedCornerShape(m.radiusPill),
        modifier = modifier
            .padding(horizontal = m.gapSm, vertical = m.gapSm)
            .graphicsLayer { translationX = shift }
            .pointerInput(Unit) {
                val limit = m.swipeDistance.toPx()
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            shift < -limit && current.hasNext -> next()
                            shift > limit && current.hasPrevious -> previous()
                        }
                        shift = 0f
                    },
                    onDragCancel = { shift = 0f },
                ) { _, dx -> shift += dx }
            },
    ) {
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(m.miniHeight)
                    .clickable(onClick = onOpen)
                    .semantics { onClick(label = openLabel) { onOpen(); true } }
                    .padding(start = m.gapMd, end = m.gapXs),
            ) {
                Artwork(now.artworkUri, m.miniArtwork, Modifier.nowPlayingCover(), radius = m.miniArtwork / 2)
                Column(modifier = Modifier.weight(1f).padding(horizontal = m.gapMd)) {
                    Text(
                        now.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        now.artist ?: stringResource(R.string.unknown_artist),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.contentMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onPrevious, enabled = now.hasPrevious, colors = buttons) {
                    Icon(painterResource(Icons.previous), stringResource(R.string.previous), Modifier.size(m.iconMd))
                }
                IconButton(onClick = onToggle, colors = buttons) {
                    val (icon, label) = when (now.isPlaying) {
                        true -> Icons.pause to R.string.pause
                        false -> Icons.play to R.string.play
                    }
                    Icon(painterResource(icon), stringResource(label), Modifier.size(m.iconLg))
                }
                IconButton(onClick = onNext, enabled = now.hasNext, colors = buttons) {
                    Icon(painterResource(Icons.next), stringResource(R.string.next), Modifier.size(m.iconMd))
                }
            }
            // Inset by the corner radius and lifted off the edge, so the line sits inside the pill.
            val played = when (now.durationMs > 0) {
                true -> (now.positionMs.toFloat() / now.durationMs).coerceIn(0f, 1f)
                false -> 0f
            }
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = m.radiusPill, end = m.radiusPill, bottom = m.gapXs)
                    .fillMaxWidth()
                    .height(m.miniProgress)
                    .background(colors.contentMuted.copy(alpha = TRACK_ALPHA)),
            ) {
                Box(Modifier.fillMaxWidth(played).fillMaxHeight().background(colors.content))
            }
        }
    }
}

private const val TRACK_ALPHA = 0.3f
