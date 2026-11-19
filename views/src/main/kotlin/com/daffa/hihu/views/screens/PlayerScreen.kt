package com.daffa.hihu.views.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.daffa.hihu.state.NowPlaying
import com.daffa.hihu.state.Playback
import com.daffa.hihu.state.Repeat
import com.daffa.hihu.state.SongLyrics
import androidx.compose.runtime.saveable.rememberSaveable
import com.daffa.hihu.ui.CoverColors
import com.daffa.hihu.ui.DarkSystemBars
import com.daffa.hihu.ui.HihuTheme
import com.daffa.hihu.ui.Icons
import com.daffa.hihu.views.R
import com.daffa.hihu.views.chrome.LocalSongActions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daffa.hihu.views.shared.Artwork
import com.daffa.hihu.views.shared.formatDuration
import com.daffa.hihu.views.shared.rememberCoverColors

/**
 * The full-screen now-playing view. The background runs from near-black into the cover's
 * colour; below the cover sit the titles, the seek bar and the transport row.
 */
@Composable
fun PlayerScreen(
    now: NowPlaying,
    playback: Playback,
    lyrics: SongLyrics,
    onClose: () -> Unit,
    onQueue: () -> Unit,
) {
    val m = HihuTheme.metrics
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    val colors = rememberCoverColors(now.artworkUri)
    DarkSystemBars()
    val background = Brush.verticalGradient(listOf(colors.base, colors.base, colors.tint))

    CompositionLocalProvider(LocalContentColor provides colors.content) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .systemBarsPadding()
                .padding(horizontal = m.gapXl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth()) {
                IconButton(onClick = onClose) {
                    Icon(painterResource(Icons.collapse), stringResource(R.string.close_player), Modifier.size(m.iconLg))
                }
                Box(Modifier.weight(1f))
                FavoriteButton(now)
                val lyricsState = stringResource(if (showLyrics) R.string.state_on else R.string.state_off)
                IconButton(
                    onClick = { showLyrics = !showLyrics },
                    modifier = Modifier.semantics { stateDescription = lyricsState },
                ) {
                    Icon(
                        painterResource(Icons.lyrics),
                        stringResource(R.string.lyrics),
                        tint = if (showLyrics) colors.content else colors.contentMuted,
                        modifier = Modifier.size(m.iconMd),
                    )
                }
                IconButton(onClick = onQueue) {
                    Icon(painterResource(Icons.queue), stringResource(R.string.queue), Modifier.size(m.iconMd))
                }
            }
            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                when (showLyrics) {
                    true -> LyricsPane(lyrics, now, colors, onSeek = playback::seekTo)
                    false -> {
                        val side = minOf(maxWidth, maxHeight, m.playerArtworkMax)
                        Artwork(now.artworkUri, side, radius = m.radiusLg)
                    }
                }
            }
            Text(
                now.title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = m.gapXl),
            )
            Text(
                now.artist ?: stringResource(R.string.unknown_artist),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.contentMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = m.gapXs),
            )
            SeekBar(now, colors, onSeek = playback::seekTo)
            Transport(now, playback, colors)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeekBar(now: NowPlaying, colors: CoverColors, onSeek: (Long) -> Unit) {
    val m = HihuTheme.metrics
    // While the thumb is held the slider shows the drag position instead of the live one.
    var dragging by remember { mutableStateOf<Float?>(null) }
    val position = dragging ?: now.positionMs.toFloat()
    val duration = now.durationMs.toFloat().coerceAtLeast(1f)
    val label = stringResource(R.string.seek)
    val sliderColors = SliderDefaults.colors(
        thumbColor = colors.content,
        activeTrackColor = colors.content,
        inactiveTrackColor = colors.contentMuted.copy(alpha = INACTIVE_TRACK_ALPHA),
    )

    Column(Modifier.fillMaxWidth().padding(top = m.gapXl)) {
        Slider(
            value = position.coerceIn(0f, duration),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeek(it.toLong()) }
                dragging = null
            },
            valueRange = 0f..duration,
            colors = sliderColors,
            // A thin line with a round thumb, rather than Material's thick track and bar thumb.
            thumb = {
                Box(Modifier.size(m.seekThumb).background(colors.content, CircleShape))
            },
            track = { state ->
                SliderDefaults.Track(
                    sliderState = state,
                    colors = sliderColors,
                    drawStopIndicator = null,
                    thumbTrackGapSize = m.none,
                    trackInsideCornerSize = m.none,
                    modifier = Modifier.height(m.seekTrack),
                )
            },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(position.toLong()), style = MaterialTheme.typography.labelMedium, color = colors.contentMuted)
            Text(formatDuration(now.durationMs), style = MaterialTheme.typography.labelMedium, color = colors.contentMuted)
        }
    }
}

@Composable
private fun Transport(now: NowPlaying, playback: Playback, colors: CoverColors) {
    val m = HihuTheme.metrics
    val toggled = IconButtonDefaults.iconButtonColors(contentColor = colors.content, disabledContentColor = colors.contentMuted)
    val idle = IconButtonDefaults.iconButtonColors(contentColor = colors.contentMuted, disabledContentColor = colors.contentMuted)
    val on = stringResource(R.string.state_on)
    val off = stringResource(R.string.state_off)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(top = m.gapLg, bottom = m.gapXxl),
    ) {
        IconButton(
            onClick = playback::toggleShuffle,
            colors = if (now.shuffle) toggled else idle,
            modifier = Modifier.semantics { stateDescription = if (now.shuffle) on else off },
        ) {
            Icon(painterResource(Icons.shuffle), stringResource(R.string.shuffle), Modifier.size(m.iconMd))
        }
        IconButton(onClick = playback::previous, enabled = now.hasPrevious, colors = toggled) {
            Icon(painterResource(Icons.previous), stringResource(R.string.previous), Modifier.size(m.iconXl))
        }
        IconButton(onClick = playback::toggle, colors = toggled, modifier = Modifier.size(m.playButton)) {
            val (icon, label) = when (now.isPlaying) {
                true -> Icons.pause to R.string.pause
                false -> Icons.play to R.string.play
            }
            Icon(painterResource(icon), stringResource(label), Modifier.size(m.playButton * PLAY_ICON_SCALE))
        }
        IconButton(onClick = playback::next, enabled = now.hasNext, colors = toggled) {
            Icon(painterResource(Icons.next), stringResource(R.string.next), Modifier.size(m.iconXl))
        }
        val (repeatIcon, repeatLabel) = when (now.repeat) {
            Repeat.Off -> Icons.repeat to R.string.repeat_off
            Repeat.All -> Icons.repeat to R.string.repeat_all
            Repeat.One -> Icons.repeatOne to R.string.repeat_one
        }
        IconButton(onClick = playback::cycleRepeat, colors = if (now.repeat == Repeat.Off) idle else toggled) {
            Icon(painterResource(repeatIcon), stringResource(repeatLabel), Modifier.size(m.iconMd))
        }
    }
}

/** A heart that adds the playing song to the favourites, filled when it is one already. */
@Composable
private fun FavoriteButton(now: NowPlaying) {
    val m = HihuTheme.metrics
    val actions = LocalSongActions.current
    val favorites by actions.favorites.ids.collectAsStateWithLifecycle()
    val song = now.song ?: return
    val isFavorite = song.id in favorites
    val on = stringResource(R.string.state_on)
    val off = stringResource(R.string.state_off)
    IconButton(
        onClick = { actions.favorites.toggle(song) },
        modifier = Modifier.semantics { stateDescription = if (isFavorite) on else off },
    ) {
        Icon(
            painterResource(if (isFavorite) Icons.favorite else Icons.favoriteBorder),
            stringResource(R.string.section_favorites),
            Modifier.size(m.iconMd),
        )
    }
}

private const val INACTIVE_TRACK_ALPHA = 0.35f
private const val PLAY_ICON_SCALE = 0.7f
