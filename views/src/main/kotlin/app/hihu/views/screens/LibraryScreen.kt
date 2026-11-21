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
import app.hihu.views.chrome.SelectionBar
import app.hihu.views.R
import app.hihu.views.chrome.CommonSongItems
import app.hihu.views.shared.Artwork
import app.hihu.views.shared.FilterField
import app.hihu.views.shared.Message
import app.hihu.views.shared.ScreenTitle
import app.hihu.views.shared.SongList
import app.hihu.views.shared.matching
import app.hihu.views.shared.matchingSongs
import app.hihu.views.shared.sortedFor
import app.hihu.state.LibrarySort
import java.text.Collator
import app.hihu.state.Preferences
import app.hihu.state.SortedList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.onClick

/** The parts of the library, one chip each. */
enum class LibrarySection { Account, Playlists, Favorites, Downloads, History, Device }

/**
 * The user's own music: playlists, hearted songs, what was played, and the files on the device.
 * A row of chips picks one, and it shows on a rounded sheet under the title. The search button
 * opens a field that narrows whichever part is shown; the text stays when the part changes.
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
    preferences: Preferences,
) {
    val m = HihuTheme.metrics
    // The sheet runs behind the floating mini player; only the list leaves room for it.
    val padding = PaddingValues(bottom = contentPadding.calculateBottomPadding())
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenTitle(
            stringResource(R.string.brand),
            onSettings = onSettings,
            modifier = Modifier.padding(top = m.titleGap),
            onSearch = when (searching) {
                true -> null
                false -> ({ searching = true })
            },
        )
        if (searching) {
            FilterField(
                text = query,
                onTextChange = { query = it },
                onClose = {
                    searching = false
                    query = ""
                },
                modifier = Modifier.padding(horizontal = m.gapLg, vertical = m.gapSm),
            )
        }
        SectionChips(section, onSelect = onSection)
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(topStart = m.radiusSheet, topEnd = m.radiusSheet),
            modifier = Modifier.fillMaxSize(),
        ) {
            when (section) {
                LibrarySection.Account -> AccountLibrary(account, query, padding, onOpenCollection, onSignIn)
                LibrarySection.Playlists -> {
                    val all by playlists.all.collectAsStateWithLifecycle()
                    val sorting = rememberSorting(preferences, SortedList.Playlists)
                    val shown = remember(all, query, sorting.current) { all.matching(query) { it.name }.sortedFor(sorting.current) }
                    PlaylistList(shown, query, sorting, padding, onOpenPlaylist, onNewPlaylist, onImportPlaylist)
                }
                LibrarySection.Favorites -> {
                    val songs by favorites.songs.collectAsStateWithLifecycle()
                    SavedSongs(songs, query, stringResource(R.string.favorites_empty), playback, now, padding, rememberSorting(preferences, SortedList.Favorites))
                }
                LibrarySection.Downloads -> {
                    val songs by downloads.songs.collectAsStateWithLifecycle()
                    SavedSongs(songs, query, stringResource(R.string.downloads_empty), playback, now, padding, rememberSorting(preferences, SortedList.Downloads))
                }
                LibrarySection.History -> {
                    val songs by history.recent.collectAsStateWithLifecycle()
                    SavedSongs(songs, query, stringResource(R.string.history_empty), playback, now, padding)
                }
                LibrarySection.Device -> DeviceSongs(library, query, playback, now, padding, rememberSorting(preferences, SortedList.Device))
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

/**
 * "New playlist" and "Import from YouTube" rows, then every playlist with its cover and song count.
 * [playlists] are the ones that match [query], already narrowed.
 */
@Composable
private fun PlaylistList(
    playlists: List<PlaylistSummary>,
    query: String,
    sorting: Sorting,
    padding: PaddingValues,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
    onImport: () -> Unit,
) {
    val m = HihuTheme.metrics
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item(key = "new") { ActionRow(Icons.add, R.string.playlist_new, onNew) }
        item(key = "import") { ActionRow(Icons.link, R.string.import_title, onImport) }
        if (playlists.isNotEmpty()) {
            item(key = "sort") {
                SortLabel(
                    sorting,
                    pluralStringResource(R.plurals.playlist_count, playlists.size, playlists.size),
                    Modifier.fillMaxWidth().padding(start = m.gapXl, end = m.gapLg, top = m.gapSm, bottom = m.gapXs),
                )
            }
        }
        if (playlists.isEmpty()) {
            item(key = "empty") {
                Text(
                    when (query.isBlank()) {
                        true -> stringResource(R.string.playlists_empty)
                        false -> stringResource(R.string.library_search_empty, query.trim())
                    },
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

/** A list's order and how to change it. History has none: it is always most recent first. */
private class Sorting(val current: LibrarySort, val options: List<LibrarySort>, val onChange: (LibrarySort) -> Unit)

/** The saved order of [list], with the orders it offers; files on the phone have no date added. */
@Composable
private fun rememberSorting(preferences: Preferences, list: SortedList): Sorting {
    val current by preferences.sort(list).collectAsStateWithLifecycle()
    val options = when (list) {
        SortedList.Device -> listOf(LibrarySort.Title, LibrarySort.Artist)
        SortedList.Favorites, SortedList.Downloads -> listOf(LibrarySort.Added, LibrarySort.Title, LibrarySort.Artist)
        SortedList.Playlists -> listOf(LibrarySort.Added, LibrarySort.Title, LibrarySort.Count)
    }
    return remember(current, list) { Sorting(current, options) { preferences.setSort(list, it) } }
}

/**
 * Favourites, downloads or history: a play-all header over the songs that match [query], in
 * [sorting]'s order, or a hint when there are none.
 */
@Composable
private fun SavedSongs(
    all: List<Song>,
    query: String,
    empty: String,
    playback: Playback,
    now: NowPlaying,
    padding: PaddingValues,
    sorting: Sorting? = null,
) {
    val songs = remember(all, query, sorting?.current) {
        all.matchingSongs(query).let { found -> sorting?.let { found.sortedFor(it.current) } ?: found }
    }
    if (all.isEmpty()) return Message(empty)
    if (songs.isEmpty()) return Message(stringResource(R.string.library_search_empty, query.trim()))
    Column {
        SheetHeader(
            sorting = sorting,
            count = songs.size,
            onShuffle = { playback.playAll(songs, shuffle = true) },
            onPlayAll = { playback.playAll(songs, shuffle = false) },
        )
        SongList(
            songs = songs,
            playingId = now.songId,
            padding = padding,
            onPlay = { index -> playback.play(songs, index) },
            selectionBar = { chosen, done -> SelectionBar(chosen, done) },
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
private fun DeviceSongs(library: Library, query: String, playback: Playback, now: NowPlaying, padding: PaddingValues, sorting: Sorting) {
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
    val songs = remember(state.songs, query, sorting.current) { state.songs.matchingSongs(query).sortedFor(sorting.current) }

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
        songs.isEmpty() -> Message(stringResource(R.string.library_search_empty, query.trim()))
        else -> Column {
            SheetHeader(
                sorting = sorting,
                count = songs.size,
                onShuffle = { playback.playAll(songs, shuffle = true) },
                onPlayAll = { playback.playAll(songs, shuffle = false) },
            )
            SongList(
                songs = songs,
                playingId = now.songId,
                padding = padding,
                onPlay = { index -> playback.play(songs, index) },
                selectionBar = { chosen, done -> SelectionBar(chosen, done) },
                menu = { song, _, dismiss -> CommonSongItems(song, dismiss) },
            )
        }
    }
}

/**
 * The order (when the list has one) and song count on the left, Shuffle and Play all on the right.
 * Tapping the order opens a menu of the others.
 */
@Composable
private fun SheetHeader(sorting: Sorting?, count: Int, onShuffle: () -> Unit, onPlayAll: () -> Unit) {
    val m = HihuTheme.metrics
    val buttons = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = m.gapXl, end = m.gapLg, top = m.gapLg, bottom = m.gapSm),
    ) {
        SortLabel(sorting, pluralStringResource(R.plurals.library_count, count, count), Modifier.weight(1f))
        FilledTonalIconButton(onClick = onShuffle, colors = buttons, modifier = Modifier.size(m.roundButton)) {
            Icon(painterResource(Icons.shuffle), stringResource(R.string.shuffle_all), Modifier.size(m.iconMd))
        }
        FilledTonalIconButton(onClick = onPlayAll, colors = buttons, modifier = Modifier.padding(start = m.gapSm).size(m.roundButton)) {
            Icon(painterResource(Icons.play), stringResource(R.string.play_all), Modifier.size(m.iconMd))
        }
    }
}

/**
 * The order a list is in (when it has one) above [countText]; tapping it opens a menu of the
 * other orders.
 */
@Composable
private fun SortLabel(sorting: Sorting?, countText: String, modifier: Modifier = Modifier) {
    val m = HihuTheme.metrics
    val label = sorting?.let { stringResource(sortLabel(it.current)) }
    val sortedBy = label?.let { stringResource(R.string.sorted_by, it) }
    val change = stringResource(R.string.sort_change)
    var choosing by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .then(
                    when (sorting) {
                        null -> Modifier
                        else -> Modifier.clip(RoundedCornerShape(m.radiusSm)).clickable(onClickLabel = change) { choosing = true }
                    },
                )
                .clearAndSetSemantics {
                    contentDescription = if (sortedBy != null) "$sortedBy, $countText" else countText
                    if (sorting != null) onClick(label = change) { choosing = true; true }
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
        if (sorting != null) {
            DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
                sorting.options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(sortLabel(option))) },
                        trailingIcon = {
                            if (option == sorting.current) Icon(painterResource(Icons.check), contentDescription = null, Modifier.size(m.iconMd))
                        },
                        onClick = {
                            choosing = false
                            sorting.onChange(option)
                        },
                    )
                }
            }
        }
    }
}

private fun sortLabel(sort: LibrarySort): Int = when (sort) {
    LibrarySort.Added -> R.string.sort_added
    LibrarySort.Title -> R.string.sort_title
    LibrarySort.Artist -> R.string.sort_artist
    LibrarySort.Count -> R.string.sort_count
}

/** Playlists in [sort]'s order: newest first as they come, by name, or the fullest first. */
private fun List<PlaylistSummary>.sortedFor(sort: LibrarySort): List<PlaylistSummary> {
    val words = Collator.getInstance().apply { strength = Collator.PRIMARY }
    return when (sort) {
        LibrarySort.Added, LibrarySort.Artist -> this
        LibrarySort.Title -> sortedWith(compareBy(words) { it.name })
        LibrarySort.Count -> sortedByDescending { it.count }
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
