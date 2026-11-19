package com.daffa.hihu.views.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daffa.hihu.music.Song
import com.daffa.hihu.state.NowPlaying
import com.daffa.hihu.state.Playback
import com.daffa.hihu.state.Playlists
import com.daffa.hihu.ui.HihuTheme
import com.daffa.hihu.ui.Icons
import com.daffa.hihu.views.R
import com.daffa.hihu.views.chrome.CommonSongItems
import com.daffa.hihu.views.chrome.ConfirmDialog
import com.daffa.hihu.views.chrome.LocalSongActions
import com.daffa.hihu.views.chrome.MenuItem
import com.daffa.hihu.views.chrome.NameDialog
import com.daffa.hihu.views.shared.Artwork
import com.daffa.hihu.views.shared.Message
import com.daffa.hihu.views.shared.SongList

/**
 * One of the user's playlists: cover, name and song count over its songs. The ⋮ menu at the
 * top renames or deletes it; each song's menu removes or moves it.
 */
@Composable
fun PlaylistScreen(
    id: Long,
    playlists: Playlists,
    playback: Playback,
    now: NowPlaying,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val m = HihuTheme.metrics
    val all by playlists.all.collectAsStateWithLifecycle()
    val songs by remember(id) { playlists.songs(id) }.collectAsStateWithLifecycle(initialValue = null)
    val summary = all.firstOrNull { it.id == id }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = m.gapXs)) {
            IconButton(onClick = onBack) {
                Icon(painterResource(Icons.back), stringResource(R.string.back), Modifier.size(m.iconMd))
            }
            Box(Modifier.weight(1f))
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(painterResource(Icons.more), stringResource(R.string.more), Modifier.size(m.iconMd))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    val actions = LocalSongActions.current
                    if (songs.orEmpty().any(actions.downloads::supports)) {
                        MenuItem(R.string.download_all) {
                            menuOpen = false
                            actions.download(songs.orEmpty())
                        }
                    }
                    MenuItem(R.string.playlist_rename) {
                        menuOpen = false
                        renaming = true
                    }
                    MenuItem(R.string.playlist_delete) {
                        menuOpen = false
                        deleting = true
                    }
                }
            }
        }
        val list = songs
        when {
            list == null || summary == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Column {
                Header(summary.name, summary.artworkUri, list, playback)
                Message(stringResource(R.string.playlist_empty))
            }
            else -> SongList(
                songs = list,
                playingId = now.songId,
                padding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                onPlay = { index -> playback.play(list, index) },
                header = { Header(summary.name, summary.artworkUri, list, playback) },
                onMove = { from, to -> playlists.move(id, from, to) },
                menu = { song, index, dismiss ->
                    CommonSongItems(song, dismiss)
                    MenuItem(R.string.remove_from_playlist) {
                        playlists.remove(id, song.id)
                        dismiss()
                    }
                    if (index > 0) MenuItem(R.string.move_up) {
                        playlists.move(id, index, index - 1)
                        dismiss()
                    }
                    if (index < list.lastIndex) MenuItem(R.string.move_down) {
                        playlists.move(id, index, index + 1)
                        dismiss()
                    }
                },
            )
        }
    }

    if (renaming && summary != null) {
        NameDialog(
            title = R.string.playlist_rename,
            initial = summary.name,
            confirm = R.string.save,
            onConfirm = {
                playlists.rename(id, it)
                renaming = false
            },
            onDismiss = { renaming = false },
        )
    }
    if (deleting && summary != null) {
        ConfirmDialog(
            message = stringResource(R.string.playlist_delete_confirm, summary.name),
            confirm = R.string.delete,
            onConfirm = {
                deleting = false
                playlists.delete(id)
                onBack()
            },
            onDismiss = { deleting = false },
        )
    }
}

@Composable
private fun Header(name: String, artworkUri: String?, songs: List<Song>, playback: Playback) {
    val m = HihuTheme.metrics
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(horizontal = m.gapXl, vertical = m.gapLg),
    ) {
        Artwork(artworkUri, m.collectionArtwork, radius = m.radiusLg)
        Text(
            name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = m.gapLg),
        )
        Text(
            pluralStringResource(R.plurals.library_count, songs.size, songs.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = m.gapXs),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(m.gapMd), modifier = Modifier.padding(top = m.gapLg)) {
            Button(onClick = { playback.playAll(songs, shuffle = false) }, enabled = songs.isNotEmpty()) {
                Icon(painterResource(Icons.play), contentDescription = null, Modifier.size(m.iconSm))
                Text(stringResource(R.string.play_all), Modifier.padding(start = m.gapSm))
            }
            FilledTonalButton(onClick = { playback.playAll(songs, shuffle = true) }, enabled = songs.isNotEmpty()) {
                Icon(painterResource(Icons.shuffle), contentDescription = null, Modifier.size(m.iconSm))
                Text(stringResource(R.string.shuffle), Modifier.padding(start = m.gapSm))
            }
        }
    }
}
