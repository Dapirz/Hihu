package app.hihu.views.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hihu.music.Collection
import app.hihu.music.Song
import app.hihu.state.Account
import app.hihu.state.Downloads
import app.hihu.state.Favorites
import app.hihu.state.History
import app.hihu.state.Library
import app.hihu.state.NowPlaying
import app.hihu.state.Playback
import app.hihu.state.PlaylistSummary
import app.hihu.state.Playlists
import app.hihu.ui.HihuTheme
import app.hihu.ui.Icons
import app.hihu.views.R
import app.hihu.views.chrome.CommonSongItems
import app.hihu.views.shared.Artwork
import app.hihu.views.shared.Message
import app.hihu.views.shared.ScreenTitle
import app.hihu.views.shared.SongList

/** The parts of the library, one chip each. */
enum class LibrarySection { Account, Playlists, Favorites, Downloads, History, Device }

/**
 * The user's own music: playlists, hearted songs, what was played, and the files on the device.
 * A row of chips picks one, and it shows on a rounded sheet under the title.
 */
@Composable
fun LibraryScreen(
    library: Library,
    playlists: Playlists,
    favorites: Favorites,
    downloads: Downloads,
    history: History,
    playback: Playback,
    now: NowPlaying,
    contentPadding: PaddingValues,
    onSettings: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onNewPlaylist: () -> Unit,
    onImportPlaylist: () -> Unit,
    section: LibrarySection,
    onSection: (LibrarySection) -> Unit,
    account: Account,
    onOpenCollection: (Collection) -> Unit,
    onSignIn: () -> Unit,
) {
    val m = HihuTheme.metrics
    // The sheet runs behind the floating mini player; only the list leaves room for it.
    val padding = PaddingValues(bottom = contentPadding.calculateBottomPadding())

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenTitle(stringResource(R.string.brand), onSettings = onSettings, modifier = Modifier.padding(top = m.gapLg))
        SectionChips(section, onSelect = onSection)
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(topStart = m.radiusSheet, topEnd = m.radiusSheet),
            modifier = Modifier.fillMaxSize(),
        ) {
            when (section) {
                LibrarySection.Account -> AccountLibrary(account, padding, onOpenCollection, onSignIn)
                LibrarySection.Playlists -> {
                    val all by playlists.all.collectAsStateWithLifecycle()
                    PlaylistList(all, padding, onOpenPlaylist, onNewPlaylist, onImportPlaylist)
                }
                LibrarySection.Favorites -> {
                    val songs by favorites.songs.collectAsStateWithLifecycle()
                    SavedSongs(songs, stringResource(R.string.favorites_empty), playback, now, padding)
                }
                LibrarySection.Downloads -> {
                    val songs by downloads.songs.collectAsStateWithLifecycle()
                    SavedSongs(songs, stringResource(R.string.downloads_empty), playback, now, padding)
                }
                LibrarySection.History -> {
                    val songs by history.recent.collectAsStateWithLifecycle()
                    SavedSongs(songs, stringResource(R.string.history_empty), playback, now, padding)
                }
                LibrarySection.Device -> DeviceSongs(library, playback, now, padding)
            }
        }
    }
}

@Composable
private fun SectionChips(selected: LibrarySection, onSelect: (LibrarySection) -> Unit) {
    val m = HihuTheme.metrics
    Row(
        horizontalArrangement = Arrangement.spacedBy(m.gapSm),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = m.gapLg, vertical = m.gapMd),
    ) {
        LibrarySection.entries.forEach { section ->
            val label = when (section) {
                LibrarySection.Account -> R.string.section_account
                LibrarySection.Playlists -> R.string.section_playlists
                LibrarySection.Favorites -> R.string.section_favorites
                LibrarySection.Downloads -> R.string.section_downloads
                LibrarySection.History -> R.string.section_history
                LibrarySection.Device -> R.string.section_device
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

/** "New playlist" and "Import from YouTube" rows, then every playlist with its cover and song count. */
@Composable
private fun PlaylistList(
    playlists: List<PlaylistSummary>,
    padding: PaddingValues,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
    onImport: () -> Unit,
) {
    val m = HihuTheme.metrics
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item(key = "new") { ActionRow(Icons.add, R.string.playlist_new, onNew) }
        item(key = "import") { ActionRow(Icons.link, R.string.import_title, onImport) }
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

/**
 * The music files on the device. Asks for the audio permission first, then reads them once. Once
 * Android stops showing the request, after it was turned down for good, the button opens Hihu's
 * page in the system settings instead, and the permission is read again on coming back.
 */
@Composable
private fun DeviceSongs(library: Library, playback: Playback, now: NowPlaying, padding: PaddingValues) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val permission = audioPermission()
    var granted by remember {
        mutableStateOf(context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED)
    }
    var blocked by rememberSaveable { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        granted = allowed
        blocked = !allowed && activity?.shouldShowRequestPermissionRationale(permission) == false
    }
    LifecycleResumeEffect(permission) {
        granted = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        onPauseOrDispose {}
    }
    val state by library.state.collectAsStateWithLifecycle()

    LaunchedEffect(granted) {
        if (granted && !state.loaded && !state.loading) library.refresh()
    }

    when {
        !granted -> Message(stringResource(R.string.library_permission), stringResource(R.string.library_allow)) {
            when (blocked) {
                true -> context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
                false -> request.launch(permission)
            }
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

/** A row that starts something, with an icon where a cover would be. */
@Composable
private fun ActionRow(icon: Int, label: Int, onClick: () -> Unit) {
    val m = HihuTheme.metrics
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = m.gapLg, vertical = m.gapMd),
    ) {
        Box(Modifier.size(m.rowArtwork), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, Modifier.size(m.iconLg))
        }
        Text(stringResource(label), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = m.gapLg))
    }
}
