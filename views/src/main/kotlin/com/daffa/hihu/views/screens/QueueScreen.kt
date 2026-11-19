package com.daffa.hihu.views.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daffa.hihu.state.NowPlaying
import com.daffa.hihu.state.Playback
import com.daffa.hihu.state.Radio
import com.daffa.hihu.ui.HihuTheme
import com.daffa.hihu.ui.Icons
import com.daffa.hihu.views.R
import com.daffa.hihu.views.chrome.MenuItem
import com.daffa.hihu.views.shared.Message
import com.daffa.hihu.views.shared.SongList

/**
 * The play queue. Tapping a song jumps to it; its menu removes it or moves it up or down.
 * The song playing now is in the accent colour.
 */
@Composable
fun QueueScreen(playback: Playback, radio: Radio, now: NowPlaying, onClose: () -> Unit) {
    val m = HihuTheme.metrics
    val queue by playback.queue.collectAsStateWithLifecycle()
    val seed by radio.seed.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = m.gapXs)) {
            IconButton(onClick = onClose) {
                Icon(painterResource(Icons.collapse), stringResource(R.string.back), Modifier.size(m.iconLg))
            }
            Column(Modifier.padding(start = m.gapSm)) {
                Text(stringResource(R.string.queue), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                val count = pluralStringResource(R.plurals.library_count, queue.size, queue.size)
                Text(
                    seed?.let { stringResource(R.string.radio_from, it.title) + " · " + count } ?: count,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when (queue.isEmpty()) {
            true -> Message(stringResource(R.string.queue_empty))
            false -> SongList(
                songs = queue,
                playingId = now.songId,
                padding = PaddingValues(),
                onPlay = playback::skipTo,
                modifier = Modifier.navigationBarsPadding(),
                // A song can be queued twice; its key counts which time this is, so it stays with
                // the same row when rows move.
                key = { index, song -> "${song.id}#${queue.subList(0, index).count { it.id == song.id }}" },
                isPlaying = { index, _ -> index == now.index },
                onMove = playback::move,
                menu = { _, index, dismiss ->
                    MenuItem(R.string.remove_from_queue) {
                        playback.removeAt(index)
                        dismiss()
                    }
                    if (index > 0) MenuItem(R.string.move_up) {
                        playback.move(index, index - 1)
                        dismiss()
                    }
                    if (index < queue.lastIndex) MenuItem(R.string.move_down) {
                        playback.move(index, index + 1)
                        dismiss()
                    }
                },
            )
        }
    }
}
