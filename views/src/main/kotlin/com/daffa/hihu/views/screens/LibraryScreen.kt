package com.daffa.hihu.views.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daffa.hihu.music.Song
import com.daffa.hihu.state.Favorites
import com.daffa.hihu.state.History
import com.daffa.hihu.state.Library
import com.daffa.hihu.state.NowPlaying
import com.daffa.hihu.state.Playback
import com.daffa.hihu.state.PlaylistSummary
import com.daffa.hihu.state.Playlists
import com.daffa.hihu.ui.HihuTheme
import com.daffa.hihu.ui.Icons
import com.daffa.hihu.views.R
import com.daffa.hihu.views.chrome.CommonSongItems
import com.daffa.hihu.views.shared.Artwork
import com.daffa.hihu.views.shared.Message
import com.daffa.hihu.views.shared.ScreenTitle
import com.daffa.hihu.views.shared.SongList

/** The parts of the library, one chip each. */
private enum class Section { Playlists, Favorites, History, Device }

/**
 * The user's own music: playlists, hearted songs, what was played, and the files on the device.
 * A row of chips picks one, and it shows on a rounded sheet under the title.
 */
@Composable
fun LibraryScreen(
    library: Library,
    playlists: Playlists,
    favorites: Favorites,
    history: History,
    playback: Playback,
    now: NowPlaying,
    contentPadding: PaddingValues,
    onSettings: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onNewPlaylist: () -> Unit,
) {
    val m = HihuTheme.metrics
    var section by rememberSaveable { mutableStateOf(Section.Playlists) }
    // The sheet runs behind the floating mini player; only the list leaves room for it.
    val padding = PaddingValues(bottom = contentPadding.calculateBottomPadding())

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenTitle(stringResource(R.string.brand), onSettings = onSettings, modifier = Modifier.padding(top = m.gapLg))
        SectionChips(section, onSelect = { section = it })
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(topStart = m.radiusSheet, topEnd = m.radiusSheet),
            modifier = Modifier.fillMaxSize(),
        ) {
            when (section) {
                Section.Playlists -> {
                    val all by playlists.all.collectAsStateWithLifecycle()
                    PlaylistList(all, padding, onOpenPlaylist, onNewPlaylist)
                }
                Section.Favorites -> {
                    val songs by favorites.songs.collectAsStateWithLifecycle()
                    SavedSongs(songs, stringResource(R.string.favorites_empty), playback, now, padding)
                }
                Section.History -> {
                    val songs by history.recent.collectAsStateWithLifecycle()
                    SavedSongs(songs, stringResource(R.string.history_empty), playback, now, padding)
                }
                Section.Device -> DeviceSongs(library, playback, now, padding)
            }
        }
    }
}

@Composable
private fun SectionChips(selected: Section, onSelect: (Section) -> Unit) {
    val m = HihuTheme.metrics
    Row(
        horizontalArrangement = Arrangement.spacedBy(m.gapSm),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = m.gapLg, vertical = m.gapMd),
    ) {
        Section.entries.forEach { section ->
            val label = when (section) {
                Section.Playlists -> R.string.section_playlists
                Section.Favorites -> R.string.section_favorites
                Section.History -> R.string.section_history
                Section.Device -> R.string.section_device
            }
            FilterChip(
                selected = section == selected,
                onClick = { onSelect(section) },
                label = { Text(stringResource(label)) },
                shape = RoundedCornerShape(m.radiusPill),
            )
        }
    }
}

/** A "New playlist" row, then every playlist with its cover and song count. */
@Composable
private fun PlaylistList(
    playlists: List<PlaylistSummary>,
    padding: PaddingValues,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
) {
    val m = HihuTheme.metrics
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item(key = "new") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onNew).padding(horizontal = m.gapLg, vertical = m.gapMd),
            ) {
                Box(Modifier.size(m.rowArtwork), contentAlignment = Alignment.Center) {
                    Icon(painterResource(Icons.add), contentDescription = null, Modifier.size(m.iconLg))
                }
                Text(stringResource(R.string.playlist_new), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = m.gapLg))
            }
        }
        if (playlists.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.playlists_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = m.gapXl, vertical = m.gapLg),
                )
            }
        }
        items(playlists, key = { it.id }) { playlist ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { onOpen(playlist.id) }.padding(horizontal = m.gapLg, vertical = m.gapMd),
            ) {
                Artwork(playlist.artworkUri, m.rowArtwork, radius = m.radiusMd)
                Column(Modifier.weight(1f).padding(start = m.gapLg)) {
                    Text(playlist.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        pluralStringResource(R.plurals.library_count, playlist.count, playlist.count),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Favourites or history: a play-all header over the songs, or a hint when there are none. */
@Composable
private fun SavedSongs(songs: List<Song>, empty: String, playback: Playback, now: NowPlaying, padding: PaddingValues) {
    if (songs.isEmpty()) return Message(empty)
    Column {
        SheetHeader(
            label = null,
            count = songs.size,
            onShuffle = { playback.playAll(songs, shuffle = true) },
            onPlayAll = { playback.playAll(songs, shuffle = false) },
        )
        SongList(
            songs = songs,
            playingId = now.songId,
            padding = padding,
            onPlay = { index -> playback.play(songs, index) },
            menu = { song, _, dismiss -> CommonSongItems(song, dismiss) },
        )
    }
}

/** The music files on the device. Asks for the audio permission first, then reads them once. */
@Composable
private fun DeviceSongs(library: Library, playback: Playback, now: NowPlaying, padding: PaddingValues) {
    val context = LocalContext.current
    val permission = audioPermission()
    var granted by remember {
        mutableStateOf(context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED)
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val state by library.state.collectAsStateWithLifecycle()

    LaunchedEffect(granted) {
        if (granted && !state.loaded && !state.loading) library.refresh()
    }

    when {
        !granted -> Message(stringResource(R.string.library_permission), stringResource(R.string.library_allow)) {
            request.launch(permission)
        }
        state.loading && state.songs.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        state.failed -> Message(stringResource(R.string.library_failed), stringResource(R.string.retry), library::refresh)
        state.loaded && state.songs.isEmpty() ->
            Message(stringResource(R.string.library_empty), stringResource(R.string.retry), library::refresh)
        else -> Column {
            SheetHeader(
                label = stringResource(R.string.sort_name),
                count = state.songs.size,
                onShuffle = { playback.playAll(state.songs, shuffle = true) },
                onPlayAll = { playback.playAll(state.songs, shuffle = false) },
            )
            SongList(
                songs = state.songs,
                playingId = now.songId,
                padding = padding,
                onPlay = { index -> playback.play(state.songs, index) },
                menu = { song, _, dismiss -> CommonSongItems(song, dismiss) },
            )
        }
    }
}

/** The sort order (when there is one) and song count on the left, Shuffle and Play all on the right. */
@Composable
private fun SheetHeader(label: String?, count: Int, onShuffle: () -> Unit, onPlayAll: () -> Unit) {
    val m = HihuTheme.metrics
    val countText = pluralStringResource(R.plurals.library_count, count, count)
    val sortedBy = stringResource(R.string.sorted_by_name)
    val buttons = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = m.gapXl, end = m.gapLg, top = m.gapLg, bottom = m.gapSm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).clearAndSetSemantics {
                contentDescription = if (label != null) "$sortedBy, $countText" else countText
            },
        ) {
            if (label != null) {
                Icon(
                    painterResource(Icons.sort),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(m.iconMd).padding(end = m.none),
                )
            }
            Column(Modifier.padding(start = if (label != null) m.gapMd else m.none)) {
                if (label != null) Text(label, style = MaterialTheme.typography.titleSmall)
                Text(countText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FilledTonalIconButton(onClick = onShuffle, colors = buttons, modifier = Modifier.size(m.roundButton)) {
            Icon(painterResource(Icons.shuffle), stringResource(R.string.shuffle_all), Modifier.size(m.iconMd))
        }
        FilledTonalIconButton(onClick = onPlayAll, colors = buttons, modifier = Modifier.padding(start = m.gapSm).size(m.roundButton)) {
            Icon(painterResource(Icons.play), stringResource(R.string.play_all), Modifier.size(m.iconMd))
        }
    }
}

private fun audioPermission(): String = when (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    true -> Manifest.permission.READ_MEDIA_AUDIO
    false -> Manifest.permission.READ_EXTERNAL_STORAGE
}
