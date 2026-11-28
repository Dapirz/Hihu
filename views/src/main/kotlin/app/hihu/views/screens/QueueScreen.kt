package app.hihu.views.screens

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
import app.hihu.state.NowPlaying
import app.hihu.music.Song
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.launch
import app.hihu.state.Playback
import app.hihu.state.Radio
import app.hihu.ui.HihuTheme
import app.hihu.ui.Icons
import app.hihu.views.R
import app.hihu.views.chrome.MenuItem
import app.hihu.views.shared.Message
import app.hihu.views.shared.SongList

/**
 * The play queue. Tapping a song jumps to it; its menu removes it or moves it up or down.
 * The song playing now is in the accent colour.
 */
@Composable
fun QueueScreen(playback: Playback, radio: Radio, now: NowPlaying, onSave: (List<Song>) -> Unit, onClose: () -> Unit) {
    val m = HihuTheme.metrics
    val queue by playback.queue.collectAsStateWithLifecycle()
    val seed by radio.seed.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    // A song can be queued twice; its key counts which time this is, so it stays with the same
    // row when rows move. Counted in one pass.
    val keys = remember(queue) {
        val seen = HashMap<String, Int>()
        queue.map { song ->
            val time = seen[song.id] ?: 0
            seen[song.id] = time + 1
            "${song.id}#$time"
        }
    }
    val scope = rememberCoroutineScope()
    val removedText = stringResource(R.string.queue_removed)
    val undoText = stringResource(R.string.undo)
    // Swiping a song away can be taken back from the bar that shows for a moment after.
    val removeWithUndo = { index: Int ->
        val song = queue.getOrNull(index)
        playback.removeAt(index)
        if (song != null) scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(removedText, actionLabel = undoText, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) playback.insertAt(index, song)
        }
        Unit
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = m.gapXs)) {
                IconButton(onClick = onClose) {
                    Icon(painterResource(Icons.collapse), stringResource(R.string.back), Modifier.size(m.iconLg))
                }
                Column(Modifier.weight(1f).padding(start = m.gapSm)) {
                    Text(stringResource(R.string.queue), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    val count = pluralStringResource(R.plurals.library_count, queue.size, queue.size)
                    Text(
                        seed?.let { stringResource(R.string.queue_subtitle_radio, stringResource(R.string.radio_from, it), count) } ?: count,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onSave(queue) }, enabled = queue.isNotEmpty()) {
                    Icon(painterResource(Icons.add), stringResource(R.string.queue_save), Modifier.size(m.iconMd))
                }
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menu = true }, enabled = queue.size > 1) {
                        Icon(painterResource(Icons.more), stringResource(R.string.more), Modifier.size(m.iconMd))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.queue_remove_played)) },
                            // While shuffling, the songs above are not the ones that played.
                        enabled = now.index > 0 && !now.shuffle,
                            onClick = {
                                menu = false
                                playback.removePlayed()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.queue_clear_upcoming)) },
                            enabled = now.index in 0 until queue.size - 1,
                            onClick = {
                                menu = false
                                playback.clearUpcoming()
                            },
                        )
                    }
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
                    key = { index, _ -> keys[index] },
                    isPlaying = { index, _ -> index == now.index },
                    onMove = playback::move,
                    dividers = false,
                    dimmed = { index, _ -> !now.shuffle && index < now.index },
                    initialIndex = now.index,
                    onRemove = removeWithUndo,
                    // The song playing now is taken out from its menu only, so a stray swipe never stops it.
                    canRemove = { index, _ -> index != now.index },
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
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}
