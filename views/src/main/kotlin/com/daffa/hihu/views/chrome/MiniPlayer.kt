package com.daffa.hihu.views.chrome

import androidx.compose.foundation.clickable
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
import com.daffa.hihu.state.NowPlaying
import com.daffa.hihu.ui.HihuTheme
import com.daffa.hihu.ui.Icons
import com.daffa.hihu.views.R
import com.daffa.hihu.views.shared.Artwork
import com.daffa.hihu.views.shared.rememberCoverColors

/**
 * The floating pill above the bottom edge while something is loaded, tinted by the cover.
 * Tapping it opens the full player.
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
    Surface(
        color = colors.tint,
        contentColor = colors.content,
        shape = RoundedCornerShape(m.radiusPill),
        modifier = modifier.padding(horizontal = m.gapSm, vertical = m.gapSm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(m.miniHeight)
                .clickable(onClick = onOpen)
                .semantics { onClick(label = openLabel) { onOpen(); true } }
                .padding(start = m.gapMd, end = m.gapXs),
        ) {
            Artwork(now.artworkUri, m.miniArtwork, radius = m.miniArtwork / 2)
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
    }
}
