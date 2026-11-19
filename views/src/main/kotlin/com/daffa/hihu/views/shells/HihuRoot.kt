package com.daffa.hihu.views.shells

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.daffa.hihu.views.shared.LocalSongBadges
import com.daffa.hihu.views.shared.RowBadge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daffa.hihu.music.Collection
import com.daffa.hihu.music.Mood
import com.daffa.hihu.music.Song
import com.daffa.hihu.router.Destination
import com.daffa.hihu.router.Navigator
import com.daffa.hihu.state.Appearance
import com.daffa.hihu.state.Collections
import com.daffa.hihu.state.DownloadStatus
import com.daffa.hihu.state.Downloads
import com.daffa.hihu.state.Explore
import com.daffa.hihu.state.Favorites
import com.daffa.hihu.state.History
import com.daffa.hihu.state.Home
import com.daffa.hihu.state.Moods
import com.daffa.hihu.state.Library
import com.daffa.hihu.state.NowPlaying
import com.daffa.hihu.state.Playback
import com.daffa.hihu.state.Playlists
import com.daffa.hihu.state.Preferences
import com.daffa.hihu.state.Radio
import com.daffa.hihu.state.Search
import com.daffa.hihu.state.SongLyrics
import com.daffa.hihu.ui.HihuTheme
import com.daffa.hihu.ui.Icons
import com.daffa.hihu.views.shared.colors
import com.daffa.hihu.views.R
import com.daffa.hihu.views.chrome.LocalSongActions
import com.daffa.hihu.views.chrome.MiniPlayer
import com.daffa.hihu.views.chrome.NameDialog
import com.daffa.hihu.views.chrome.PlaylistPicker
import com.daffa.hihu.views.chrome.SongActions
import com.daffa.hihu.views.screens.CollectionScreen
import com.daffa.hihu.views.screens.ExploreScreen
import com.daffa.hihu.views.screens.HomeScreen
import com.daffa.hihu.views.screens.LibraryScreen
import com.daffa.hihu.views.screens.LibrarySection
import com.daffa.hihu.views.screens.MoodScreen
import com.daffa.hihu.views.screens.PlayerScreen
import com.daffa.hihu.views.screens.PlaylistScreen
import com.daffa.hihu.views.screens.QueueScreen
import com.daffa.hihu.views.screens.SearchScreen
import com.daffa.hihu.views.screens.SettingsScreen

/** Everything the UI reads and drives, built once by the app module. */
class AppState(
    val navigator: Navigator,
    val library: Library,
    val search: Search,
    val collections: Collections,
    val playlists: Playlists,
    val favorites: Favorites,
    val history: History,
    val playback: Playback,
    val radio: Radio,
    val lyrics: SongLyrics,
    val explore: Explore,
    val home: Home,
    val moods: Moods,
    val downloads: Downloads,
    val preferences: Preferences,
)

/**
 * The whole UI. The app module builds the state objects and hands them in; nothing below this
 * point creates a player, a provider or a database. It also hosts the playlist dialogs, so any
 * song's menu can open them.
 */
@Composable
fun HihuRoot(app: AppState) {
    val appearance by app.preferences.appearance.collectAsStateWithLifecycle()
    val dark = when (appearance) {
        Appearance.System -> null
        Appearance.Light -> false
        Appearance.Dark -> true
    }
    val palette by app.preferences.palette.collectAsStateWithLifecycle()
    val black by app.preferences.black.collectAsStateWithLifecycle()
    // Songs waiting for a playlist: first the picker, then the name dialog for a new one.
    var picking by remember { mutableStateOf<List<Song>?>(null) }
    var naming by remember { mutableStateOf<List<Song>?>(null) }
    val context = LocalContext.current
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val actions = remember(app) {
        SongActions(
            playback = app.playback,
            favorites = app.favorites,
            downloads = app.downloads,
            pickPlaylist = { picking = it },
            download = { songs ->
                // The progress notification needs this on Android 13 and newer; downloads run either way.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                app.downloads.download(songs)
            },
        )
    }
    val progress by app.downloads.progress.collectAsStateWithLifecycle()
    val badges = remember(progress) {
        progress.mapNotNull { (id, p) ->
            when (p.status) {
                DownloadStatus.Done -> id to RowBadge.Downloaded
                DownloadStatus.Downloading, DownloadStatus.Queued -> id to RowBadge.Downloading(p.percent.toInt())
                DownloadStatus.Failed -> null
            }
        }.toMap()
    }

    HihuTheme(dark = dark, colors = palette.colors, black = black) {
        CompositionLocalProvider(LocalSongActions provides actions, LocalSongBadges provides badges) {
            Screens(app, onNewPlaylist = { naming = emptyList() })
            Dialogs(app, picking, naming, onPicking = { picking = it }, onNaming = { naming = it })
        }
    }
}

@Composable
private fun Screens(app: AppState, onNewPlaylist: () -> Unit) {
    val navigator = app.navigator
    val stack by navigator.stack.collectAsStateWithLifecycle()
    val now by app.playback.state.collectAsStateWithLifecycle()
    val openSettings = { navigator.push(Destination.Settings) }
    val openCollection = { collection: Collection ->
        app.collections.open(collection)
        navigator.push(Destination.Collection(collection.id))
    }
    val openMood = { mood: Mood ->
        app.moods.open(mood)
        navigator.push(Destination.Mood(mood.ref, mood.title))
    }
    // A song from a feed plays like YouTube Music: it, then its radio, not the rest of the row.
    val playFromFeed = { song: Song, _: List<Song> -> app.radio.start(song) }

    BackHandler(enabled = stack.size > 1) { navigator.pop() }

    // The library's chosen chip lives here, not in the library screen, so it is still chosen
    // after the player or a playlist has covered the library and gone again.
    var librarySection by rememberSaveable { mutableStateOf(LibrarySection.Playlists) }

    when (val current = stack.last()) {
        Destination.Player -> Scaffold { _ ->
            PlayerScreen(now, app.playback, app.lyrics, onClose = { navigator.pop() }, onQueue = { navigator.push(Destination.Queue) })
        }
        Destination.Queue -> Scaffold { _ ->
            QueueScreen(app.playback, app.radio, now, onClose = { navigator.pop() })
        }
        Destination.Settings -> Scaffold { _ ->
            SettingsScreen(app.preferences, app.downloads, onBack = { navigator.pop() })
        }
        else -> Scaffold(bottomBar = { BottomChrome(navigator.tab, now, navigator, app.playback) }) { padding ->
            when (current) {
                Destination.Search -> SearchScreen(
                    search = app.search,
                    playback = app.playback,
                    radio = app.radio,
                    now = now,
                    contentPadding = padding,
                    onOpen = openCollection,
                    onSettings = openSettings,
                )
                Destination.Home -> HomeScreen(
                    home = app.home,
                    contentPadding = padding,
                    onSongs = playFromFeed,
                    onOpen = openCollection,
                    onSettings = openSettings,
                )
                Destination.Explore -> ExploreScreen(
                    explore = app.explore,
                    playback = app.playback,
                    now = now,
                    contentPadding = padding,
                    onSongs = playFromFeed,
                    onOpen = openCollection,
                    onMood = openMood,
                    onSettings = openSettings,
                )
                is Destination.Mood -> when (val open = app.moods[current.ref]) {
                    null -> LaunchedEffect(current) { navigator.pop() }
                    else -> MoodScreen(open, padding, onSongs = playFromFeed, onOpen = openCollection, onBack = { navigator.pop() })
                }
                Destination.Library -> LibraryScreen(
                    library = app.library,
                    playlists = app.playlists,
                    favorites = app.favorites,
                    downloads = app.downloads,
                    history = app.history,
                    playback = app.playback,
                    now = now,
                    contentPadding = padding,
                    onSettings = openSettings,
                    onOpenPlaylist = { navigator.push(Destination.Playlist(it)) },
                    onNewPlaylist = onNewPlaylist,
                    section = librarySection,
                    onSection = { librarySection = it },
                )
                is Destination.Collection -> when (val open = app.collections[current.id]) {
                    null -> LaunchedEffect(current) { navigator.pop() }
                    else -> CollectionScreen(open, app.playback, now, padding, onBack = { navigator.pop() })
                }
                is Destination.Playlist -> PlaylistScreen(
                    id = current.id,
                    playlists = app.playlists,
                    playback = app.playback,
                    now = now,
                    contentPadding = padding,
                    onBack = { navigator.pop() },
                )
                Destination.Player, Destination.Queue, Destination.Settings -> Unit
            }
        }
    }
}

/** The playlist picker and the new-playlist name dialog, when either is open. */
@Composable
private fun Dialogs(
    app: AppState,
    picking: List<Song>?,
    naming: List<Song>?,
    onPicking: (List<Song>?) -> Unit,
    onNaming: (List<Song>?) -> Unit,
) {
    val playlists by app.playlists.all.collectAsStateWithLifecycle()
    if (picking != null) {
        PlaylistPicker(
            playlists = playlists,
            onPick = { id ->
                app.playlists.add(id, picking)
                onPicking(null)
            },
            onNew = {
                onNaming(picking)
                onPicking(null)
            },
            onDismiss = { onPicking(null) },
        )
    }
    if (naming != null) {
        NameDialog(
            title = R.string.playlist_new,
            initial = "",
            confirm = R.string.create,
            onConfirm = { name ->
                app.playlists.create(name, naming)
                onNaming(null)
            },
            onDismiss = { onNaming(null) },
        )
    }
}

/** The floating mini player, when something is loaded, above the bottom navigation. */
@Composable
private fun BottomChrome(current: Destination.Tab, now: NowPlaying, navigator: Navigator, playback: Playback) {
    val m = HihuTheme.metrics
    Column {
        if (now.songId != null) {
            MiniPlayer(
                now = now,
                onOpen = { navigator.push(Destination.Player) },
                onToggle = playback::toggle,
                onPrevious = playback::previous,
                onNext = playback::next,
            )
        }
        NavigationBar {
            Destination.tabs.forEach { tab ->
                val (icon, label) = when (tab) {
                    Destination.Home -> Icons.home to R.string.tab_home
                    Destination.Search -> Icons.search to R.string.tab_search
                    Destination.Explore -> Icons.explore to R.string.tab_explore
                    Destination.Library -> Icons.library to R.string.tab_library
                }
                NavigationBarItem(
                    selected = tab == current,
                    onClick = { navigator.select(tab) },
                    icon = { Icon(painterResource(icon), contentDescription = null, Modifier.size(m.iconMd)) },
                    label = { Text(stringResource(label)) },
                )
            }
        }
    }
}
