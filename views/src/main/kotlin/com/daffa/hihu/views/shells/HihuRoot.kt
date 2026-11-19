package com.daffa.hihu.views.shells

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daffa.hihu.music.Song
import com.daffa.hihu.router.Destination
import com.daffa.hihu.router.Navigator
import com.daffa.hihu.state.Appearance
import com.daffa.hihu.state.Collections
import com.daffa.hihu.state.Favorites
import com.daffa.hihu.state.History
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
import com.daffa.hihu.views.R
import com.daffa.hihu.views.chrome.LocalSongActions
import com.daffa.hihu.views.chrome.MiniPlayer
import com.daffa.hihu.views.chrome.NameDialog
import com.daffa.hihu.views.chrome.PlaylistPicker
import com.daffa.hihu.views.chrome.SongActions
import com.daffa.hihu.views.screens.CollectionScreen
import com.daffa.hihu.views.screens.LibraryScreen
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
    // Songs waiting for a playlist: first the picker, then the name dialog for a new one.
    var picking by remember { mutableStateOf<List<Song>?>(null) }
    var naming by remember { mutableStateOf<List<Song>?>(null) }
    val actions = remember(app) { SongActions(app.playback, app.favorites, pickPlaylist = { picking = it }) }

    HihuTheme(dark = dark) {
        CompositionLocalProvider(LocalSongActions provides actions) {
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

    BackHandler(enabled = stack.size > 1) { navigator.pop() }

    when (val current = stack.last()) {
        Destination.Player -> Scaffold { _ ->
            PlayerScreen(now, app.playback, app.lyrics, onClose = { navigator.pop() }, onQueue = { navigator.push(Destination.Queue) })
        }
        Destination.Queue -> Scaffold { _ ->
            QueueScreen(app.playback, app.radio, now, onClose = { navigator.pop() })
        }
        Destination.Settings -> Scaffold { _ ->
            SettingsScreen(app.preferences, onBack = { navigator.pop() })
        }
        else -> Scaffold(bottomBar = { BottomChrome(navigator.tab, now, navigator, app.playback) }) { padding ->
            when (current) {
                Destination.Search -> SearchScreen(
                    search = app.search,
                    playback = app.playback,
                    radio = app.radio,
                    now = now,
                    contentPadding = padding,
                    onOpen = { collection ->
                        app.collections.open(collection)
                        navigator.push(Destination.Collection(collection.id))
                    },
                    onSettings = openSettings,
                )
                Destination.Library -> LibraryScreen(
                    library = app.library,
                    playlists = app.playlists,
                    favorites = app.favorites,
                    history = app.history,
                    playback = app.playback,
                    now = now,
                    contentPadding = padding,
                    onSettings = openSettings,
                    onOpenPlaylist = { navigator.push(Destination.Playlist(it)) },
                    onNewPlaylist = onNewPlaylist,
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
                    Destination.Search -> Icons.search to R.string.tab_search
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
