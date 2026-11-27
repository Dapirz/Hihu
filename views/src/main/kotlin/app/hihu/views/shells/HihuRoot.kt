package app.hihu.views.shells

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import app.hihu.views.shared.LocalSongBadges
import app.hihu.views.shared.RowBadge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hihu.music.Collection
import app.hihu.music.SearchKind
import app.hihu.music.Mood
import app.hihu.music.Song
import app.hihu.router.Destination
import app.hihu.router.Navigator
import app.hihu.state.Appearance
import app.hihu.state.Artists
import app.hihu.state.Collections
import app.hihu.state.DownloadStatus
import app.hihu.state.Downloads
import app.hihu.state.Explore
import app.hihu.state.Favorites
import app.hihu.state.History
import app.hihu.state.Home
import app.hihu.state.Moods
import app.hihu.state.Library
import app.hihu.state.NowPlaying
import app.hihu.state.Playback
import app.hihu.state.PlaylistImport
import app.hihu.state.Playlists
import app.hihu.state.Preferences
import app.hihu.state.Radio
import app.hihu.state.Search
import app.hihu.state.SongLyrics
import app.hihu.ui.HihuTheme
import app.hihu.ui.Icons
import app.hihu.views.shared.colors
import app.hihu.views.R
import app.hihu.views.chrome.ImportDialog
import app.hihu.views.chrome.LocalSongActions
import app.hihu.views.chrome.MiniPlayer
import app.hihu.views.chrome.NameDialog
import app.hihu.views.chrome.PlaylistPicker
import app.hihu.views.chrome.SongActions
import app.hihu.views.screens.CollectionScreen
import app.hihu.views.screens.ExploreScreen
import app.hihu.views.screens.HomeScreen
import app.hihu.views.screens.LibraryScreen
import app.hihu.views.screens.LibrarySection
import app.hihu.views.screens.MoodScreen
import app.hihu.views.screens.PlayerScreen
import app.hihu.views.screens.PlaylistScreen
import app.hihu.views.screens.QueueScreen
import app.hihu.views.screens.SearchScreen
import app.hihu.views.screens.SettingsScreen
import app.hihu.views.screens.SignInScreen
import app.hihu.state.Account
import app.hihu.ui.COMPACT_HEIGHT
import app.hihu.ui.COMPACT_SCALE
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import app.hihu.state.HiddenSongs
import app.hihu.state.BugReport
import app.hihu.music.PlaylistLinks

/** Everything the UI reads and drives, built once by the app module. */
class AppState(
    val navigator: Navigator,
    val library: Library,
    val search: Search,
    val artists: Artists,
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
    val playlistImport: PlaylistImport,
    val downloads: Downloads,
    val hidden: HiddenSongs,
    /** The log to attach to a bug report. */
    val bugReport: BugReport,
    /** Song links to share, from the provider that has them. */
    val links: PlaylistLinks,
    val preferences: Preferences,
    /** The YouTube Music account, signed in or not. */
    val account: Account,
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
    // The import dialog, open with the link it starts from; a link shared from another app opens it.
    var importing by rememberSaveable { mutableStateOf<String?>(null) }
    val shared by app.playlistImport.pending.collectAsStateWithLifecycle()
    LaunchedEffect(shared) {
        shared?.let {
            importing = it
            app.playlistImport.takePending()
        }
    }
    val context = LocalContext.current
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val actions = remember(app) {
        SongActions(
            playback = app.playback,
            favorites = app.favorites,
            downloads = app.downloads,
            hidden = app.hidden,
            radio = app.radio,
            links = app.links,
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
            artists = app.artists,
            openArtist = { name -> openArtist(app, name) },
        )
    }
    val progress by app.downloads.progress.collectAsStateWithLifecycle()
    val badges = remember(progress) {
        progress.mapNotNull { (id, p) ->
            when (p.status) {
                DownloadStatus.Done -> id to RowBadge.Downloaded
                DownloadStatus.Downloading, DownloadStatus.Queued -> id to RowBadge.Downloading(p.percent.toInt())
                DownloadStatus.Failed -> id to RowBadge.Failed
            }
        }.toMap()
    }

    // A short window, like Samsung's pop-up view over a game, gets the compact sizes and draws
    // everything a little smaller, as if zoomed out, so more of each screen fits.
    BoxWithConstraints {
        val compact = maxHeight < COMPACT_HEIGHT
        val density = LocalDensity.current
        val shown = when (compact) {
            true -> Density(density.density * COMPACT_SCALE, density.fontScale)
            false -> density
        }
        CompositionLocalProvider(LocalDensity provides shown) {
            HihuTheme(dark = dark, colors = palette.colors, black = black, compact = compact) {
                CompositionLocalProvider(LocalSongActions provides actions, LocalSongBadges provides badges) {
                    Screens(
                        app,
                        onNewPlaylist = { naming = emptyList() },
                        onImportPlaylist = { importing = "" },
                        onSaveQueue = { naming = it },
                    )
                    Dialogs(app, picking, naming, onPicking = { picking = it }, onNaming = { naming = it })
                    importing?.let { link ->
                        ImportDialog(
                            importer = app.playlistImport,
                            initial = link,
                            onOpen = { id ->
                                importing = null
                                app.navigator.push(Destination.Playlist(id))
                            },
                            onDismiss = { importing = null },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Screens(app: AppState, onNewPlaylist: () -> Unit, onImportPlaylist: () -> Unit, onSaveQueue: (List<Song>) -> Unit) {
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

    // The player, the queue, settings and sign-in cover the tabs; the tabs and what is opened
    // from them share the bottom bar. Each layer changes with a short slide and fade.
    val top = stack.last()
    val overlay = top.takeIf(::isOverlay)
    val base = stack.lastOrNull { !isOverlay(it) } ?: Destination.Home
    AnimatedContent(
        targetState = overlay,
        transitionSpec = { overlayTransition(initialState, targetState) },
        label = "overlay",
    ) { shown ->
        when (shown) {
            Destination.Player -> Scaffold { _ ->
                PlayerScreen(now, app.playback, app.lyrics, app.preferences, onClose = { navigator.pop() }, onQueue = { navigator.push(Destination.Queue) })
            }
            Destination.Queue -> Scaffold { _ ->
                QueueScreen(app.playback, app.radio, now, onSave = onSaveQueue, onClose = { navigator.pop() })
            }
            Destination.Settings -> Scaffold { _ ->
                SettingsScreen(
                    app.preferences,
                    app.downloads,
                    app.hidden,
                    app.bugReport,
                    app.account,
                    onSignIn = { navigator.push(Destination.SignIn) },
                    onBack = { navigator.pop() },
                )
            }
            Destination.SignIn -> Scaffold { _ ->
                SignInScreen(app.account, onDone = { navigator.pop() }, onBack = { navigator.pop() })
            }
            else -> Scaffold(bottomBar = { BottomChrome(navigator.tab, now, navigator, app.playback) }) { padding ->
                AnimatedContent(
                    targetState = base to stack.size,
                    transitionSpec = { screenTransition(initialState, targetState) },
                    contentKey = { it.first },
                    label = "screen",
                ) { (current, _) ->
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
                            onImportPlaylist = onImportPlaylist,
                            section = librarySection,
                            onSection = { librarySection = it },
                            account = app.account,
                            onOpenCollection = openCollection,
                            onSignIn = { navigator.push(Destination.SignIn) },
                            preferences = app.preferences,
                        )
                        is Destination.Collection -> when (val open = app.collections[current.id]) {
                            null -> LaunchedEffect(current) { navigator.pop() }
                            else -> CollectionScreen(open, app.playback, app.radio, now, padding, onBack = { navigator.pop() })
                        }
                        is Destination.Playlist -> PlaylistScreen(
                            id = current.id,
                            playlists = app.playlists,
                            playback = app.playback,
                            now = now,
                            contentPadding = padding,
                            onBack = { navigator.pop() },
                        )
                        else -> Unit
                    }
                }
            }
        }
    }
}

/** Whether [destination] covers the whole screen, bottom bar and all. */
/**
 * Opens the page of the artist [name] once it is found, over the tab the player or queue covers;
 * when no artist goes by that name, searches for artists by it instead.
 */
private fun openArtist(app: AppState, name: String) {
    val navigator = app.navigator
    val uncover = { while (isOverlay(navigator.stack.value.last()) && navigator.pop()) Unit }
    app.artists.find(
        name,
        onFound = { artist ->
            uncover()
            app.collections.open(artist)
            navigator.push(Destination.Collection(artist.id))
        },
        onMissing = {
            uncover()
            app.search.select(SearchKind.Artists)
            app.search.submit(name)
            navigator.select(Destination.Search)
        },
    )
}

private fun isOverlay(destination: Destination) = when (destination) {
    Destination.Player, Destination.Queue, Destination.Settings, Destination.SignIn -> true
    else -> false
}

/** The player rises from the bottom and sinks back; the other full screens slide in from the side. */
private fun overlayTransition(from: Destination?, to: Destination?): ContentTransform {
    val spec = tween<IntOffset>(TRANSITION_MS)
    val fade = tween<Float>(TRANSITION_MS)
    val player = to == Destination.Player || (to == null && from == Destination.Player)
    val opening = to != null && (from == null || from == Destination.Player)
    return when {
        player && opening -> (slideInVertically(spec) { it / SLIDE_PART } + fadeIn(fade)) togetherWith fadeOut(fade)
        player -> fadeIn(fade) togetherWith (slideOutVertically(spec) { it / SLIDE_PART } + fadeOut(fade))
        opening -> (slideInHorizontally(spec) { it / SLIDE_PART } + fadeIn(fade)) togetherWith fadeOut(fade)
        to == null -> fadeIn(fade) togetherWith (slideOutHorizontally(spec) { it / SLIDE_PART } + fadeOut(fade))
        else -> fadeIn(fade) togetherWith fadeOut(fade)
    }
}

/** Opening something slides it in from the right and going back slides it away; tabs just fade. */
private fun screenTransition(from: Pair<Destination, Int>, to: Pair<Destination, Int>): ContentTransform {
    val spec = tween<IntOffset>(TRANSITION_MS)
    val fade = tween<Float>(TRANSITION_MS)
    return when {
        to.second > from.second -> (slideInHorizontally(spec) { it / SLIDE_PART } + fadeIn(fade)) togetherWith fadeOut(fade)
        to.second < from.second -> fadeIn(fade) togetherWith (slideOutHorizontally(spec) { it / SLIDE_PART } + fadeOut(fade))
        else -> fadeIn(fade) togetherWith fadeOut(fade)
    }
}

private const val TRANSITION_MS = 200
/** Screens slide this fraction of their size, not all the way, so the move stays light. */
private const val SLIDE_PART = 4

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
    // Songs headed for a playlist that already holds some of them, waiting for the user to choose.
    var doubled by remember { mutableStateOf<Pair<Long, List<Song>>?>(null) }
    if (picking != null) {
        PlaylistPicker(
            playlists = playlists,
            onPick = { id ->
                val there = app.playlists.members.value[id].orEmpty()
                when (picking.any { it.id in there }) {
                    true -> doubled = id to picking
                    false -> app.playlists.add(id, picking)
                }
                onPicking(null)
            },
            onNew = {
                onNaming(picking)
                onPicking(null)
            },
            onDismiss = { onPicking(null) },
        )
    }
    doubled?.let { (id, songs) ->
        val there = app.playlists.members.value[id].orEmpty()
        val fresh = songs.filter { it.id !in there }
        val repeats = songs.size - fresh.size
        AlertDialog(
            onDismissRequest = { doubled = null },
            text = { Text(pluralStringResource(R.plurals.playlist_already_has, repeats, repeats)) },
            confirmButton = {
                TextButton(onClick = {
                    app.playlists.add(id, songs)
                    doubled = null
                }) { Text(stringResource(R.string.playlist_add_anyway)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (fresh.isNotEmpty()) app.playlists.add(id, fresh)
                    doubled = null
                }) {
                    Text(
                        when (fresh.isEmpty()) {
                            true -> stringResource(R.string.cancel)
                            false -> stringResource(R.string.playlist_skip_repeats)
                        },
                    )
                }
            },
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
        // Short on height, the bar keeps its icons and drops the labels, which the icons then say.
        NavigationBar(modifier = if (m.compact) Modifier.height(m.roundButton + m.gapSm) else Modifier) {
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
                    icon = {
                        Icon(
                            painterResource(icon),
                            contentDescription = if (m.compact) stringResource(label) else null,
                            modifier = Modifier.size(m.iconMd),
                        )
                    },
                    label = if (m.compact) null else ({ Text(stringResource(label)) }),
                )
            }
        }
    }
}
