package app.hihu.views.screens

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hihu.music.LyricLine
import app.hihu.state.NowPlaying
import app.hihu.state.SongLyrics
import app.hihu.ui.CoverColors
import app.hihu.ui.HihuTheme
import app.hihu.views.R
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * The lyrics in place of the cover. Synced lyrics follow the song, the current line bright and
 * kept a third of the way down; tapping a line seeks to it. Below them, the song's timing can be
 * moved half a second at a time when the lines run early or late. Plain lyrics just scroll.
 */
@Composable
fun LyricsPane(lyrics: SongLyrics, now: NowPlaying, colors: CoverColors, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    val m = HihuTheme.metrics
    val state by lyrics.state.collectAsStateWithLifecycle()

    DisposableEffect(lyrics) {
        lyrics.setVisible(true)
        onDispose { lyrics.setVisible(false) }
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val found = state.lyrics
        when {
            state.loading -> CircularProgressIndicator(color = colors.content)
            state.failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Note(stringResource(R.string.lyrics_failed), colors)
                TextButton(onClick = lyrics::retry) { Text(stringResource(R.string.retry), color = colors.content) }
            }
            found == null -> Note(stringResource(R.string.lyrics_missing), colors)
            found.synced != null -> Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) { Synced(found.synced!!, found.source, state.offsetMs, now, colors, onSeek) }
                Timing(state.offsetMs, colors, onShift = lyrics::shift, onReset = lyrics::resetOffset)
            }
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = m.gapLg)) {
                Text(found.plain.orEmpty(), style = MaterialTheme.typography.titleMedium, color = colors.content, lineHeight = MaterialTheme.typography.titleLarge.lineHeight)
                Source(found.source, colors)
            }
        }
    }
}

@Composable
private fun Synced(lines: List<LyricLine>, source: String, offsetMs: Long, now: NowPlaying, colors: CoverColors, onSeek: (Long) -> Unit) {
    val m = HihuTheme.metrics
    val position = smoothPosition(now)
    // -1 before the first line starts: nothing is lit yet, and the list waits at the top.
    val current = lines.indexOfLast { it.timeMs + offsetMs <= position }
    val list = rememberLazyListState()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val third = with(LocalDensity.current) { (maxHeight / 3).roundToPx() }
        LaunchedEffect(current) { list.animateScrollToItem(current.coerceAtLeast(0), -third) }
        LazyColumn(state = list, contentPadding = PaddingValues(vertical = maxHeight / 3), modifier = Modifier.fillMaxSize()) {
            itemsIndexed(lines, key = { i, line -> "$i:${line.timeMs}" }) { i, line ->
                val active = i == current
                Text(
                    line.text.ifBlank { "♪" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) colors.content else colors.contentMuted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSeek((line.timeMs + offsetMs).coerceAtLeast(0)) }
                        .padding(vertical = m.gapSm),
                )
            }
            item(key = "source") { Source(source, colors) }
        }
    }
}

/**
 * Moves the lines half a second earlier or later. The middle shows how far they are moved, and
 * tapping it puts them back.
 */
@Composable
private fun Timing(offsetMs: Long, colors: CoverColors, onShift: (Long) -> Unit, onReset: () -> Unit) {
    val m = HihuTheme.metrics
    val earlier = stringResource(R.string.lyrics_earlier_label)
    val later = stringResource(R.string.lyrics_later_label)
    val reset = stringResource(R.string.lyrics_timing_reset)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().padding(top = m.gapSm),
    ) {
        TextButton(onClick = { onShift(-STEP_MS) }, modifier = Modifier.semantics { contentDescription = earlier }) {
            Text(stringResource(R.string.lyrics_earlier), color = colors.content)
        }
        val seconds = String.format(Locale.getDefault(), "%+.1f", offsetMs / 1000.0)
        Text(
            when (offsetMs == 0L) {
                true -> stringResource(R.string.lyrics_timing)
                false -> stringResource(R.string.lyrics_timing_moved, seconds)
            },
            style = MaterialTheme.typography.labelLarge,
            color = colors.contentMuted,
            modifier = Modifier
                .clickable(enabled = offsetMs != 0L, onClickLabel = reset, onClick = onReset)
                .padding(horizontal = m.gapMd, vertical = m.gapSm),
        )
        TextButton(onClick = { onShift(STEP_MS) }, modifier = Modifier.semantics { contentDescription = later }) {
            Text(stringResource(R.string.lyrics_later), color = colors.content)
        }
    }
}

/**
 * The playback position, moved on between the player's half-second updates so a line lights up
 * on time.
 */
@Composable
private fun smoothPosition(now: NowPlaying): Long {
    var position by remember { mutableLongStateOf(now.positionMs) }
    LaunchedEffect(now.positionMs, now.isPlaying) {
        val base = now.positionMs
        val start = SystemClock.elapsedRealtime()
        position = base
        while (now.isPlaying) {
            delay(FRAME_MS)
            position = base + (SystemClock.elapsedRealtime() - start)
        }
    }
    return position
}

@Composable
private fun Note(text: String, colors: CoverColors) {
    Text(text, color = colors.contentMuted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun Source(source: String, colors: CoverColors) {
    val m = HihuTheme.metrics
    Text(
        stringResource(R.string.lyrics_source, source),
        style = MaterialTheme.typography.labelMedium,
        color = colors.contentMuted,
        modifier = Modifier.padding(top = m.gapXl),
    )
}

private const val FRAME_MS = 100L
private const val STEP_MS = 500L
