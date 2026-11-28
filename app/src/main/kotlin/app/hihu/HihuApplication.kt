package app.hihu

import android.app.Application
import android.content.ComponentName
import android.net.ConnectivityManager
import android.app.Activity
import android.os.Bundle
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import app.hihu.state.LikedDownloads
import app.hihu.music.spotify.SpotifyPlaylists
import app.hihu.state.BugReport
import app.hihu.state.RecentSources
import android.telephony.TelephonyManager
import android.util.Log
import java.util.Locale
import app.hihu.music.local.MediaStoreLibrary
import app.hihu.music.youtube.YouTubeMusic
import app.hihu.router.Destination
import app.hihu.router.Navigator
import app.hihu.state.Artists
import app.hihu.state.Collections
import app.hihu.state.Downloads
import app.hihu.state.Explore
import app.hihu.state.Favorites
import app.hihu.state.History
import app.hihu.state.HiddenSongs
import app.hihu.state.Home
import app.hihu.state.Moods
import app.hihu.state.Library
import app.hihu.state.Playback
import app.hihu.state.PlaylistImport
import app.hihu.state.Playlists
import app.hihu.state.Preferences
import app.hihu.state.Radio
import app.hihu.state.SavedQueue
import app.hihu.state.Search
import app.hihu.state.SearchHistory
import app.hihu.state.SongLyrics
import app.hihu.music.FirstLyrics
import app.hihu.music.lrclib.Lrclib
import app.hihu.state.Store
import app.hihu.views.shells.AppState
import app.hihu.state.Account
import app.hihu.state.AccountLikes
import app.hihu.state.AccountStatus
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.plus
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The composition root. This is the only place that names a concrete provider; everything else
 * sees the interfaces in the music module. State lives here so it outlives the activity.
 */
class HihuApplication : Application() {
    // Background work that fails where nothing catches it is logged, not a crash of the whole app.
    internal val scope = MainScope() + CoroutineExceptionHandler { _, e -> Log.e(TAG, "app: background work failed", e) }

    lateinit var state: AppState
        private set

    /** Sets the player's volume per song so loud songs are turned down. One per process. */
    lateinit var normalizer: Normalizer
        private set

    /** Downloads, and the data source the player reads through. One per process. */
    lateinit var offline: Offline
        private set

    override fun onCreate() {
        super.onCreate()
        val preferences = Preferences(this)
        // Mobile data and other metered networks use their own quality; read as each song opens.
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val youtube = YouTubeMusic(
            locale = contentLocale(),
            quality = {
                when (connectivity.isActiveNetworkMetered) {
                    true -> preferences.qualityMobile.value
                    false -> preferences.quality.value
                }
            },
            downloadQuality = { preferences.qualityDownload.value },
        )
        val store = Store(this)
        val history = History(store, scope)
        // Signed in, a heart is also a like on the account, sent by AccountLikes once it exists.
        lateinit var likes: AccountLikes
        lateinit var likedDownloads: LikedDownloads
        val favorites = Favorites(store, scope) { song, liked ->
            likes.toggled(song, liked)
            likedDownloads.toggled(song, liked)
        }
        lateinit var downloads: Downloads
        normalizer = Normalizer(this, youtube, preferences.normalize, scope) { uri ->
            downloads.songs.value.any { it.uri == uri }
        }
        offline = Offline(this, listOf(youtube), onDownload = normalizer::prefetch)
        downloads = Downloads(offline, store, scope)
        val playback = Playback(
            context = this,
            service = ComponentName(this, PlaybackService::class.java),
            scope = scope,
            saved = SavedQueue(store),
            onSongStarted = history::record,
            versions = youtube,
            albumVersions = preferences.albumVersions,
            downloaded = downloads::isDone,
        )
        val playlists = Playlists(store, scope)
        // A hidden song leaves the queue now, and signed in it is disliked on the account too.
        val hidden = HiddenSongs(store, scope) { song, gone ->
            if (gone) playback.drop(song.id)
            likes.disliked(song, gone)
        }
        // Signing in is optional; signed out, everything works anonymously.
        val account = Account(youtube, this, scope)
        likes = AccountLikes(youtube, account.status, store, scope)
        likedDownloads = LikedDownloads(
            enabled = preferences.downloadLikes,
            mobileToo = preferences.downloadLikesMobile,
            preferences = preferences,
            downloads = downloads,
            favorites = favorites::all,
            unmetered = { !connectivity.isActiveNetworkMetered },
        )
        watchUnmetered(connectivity, likedDownloads)
        watchVisible()
        state = AppState(
            navigator = Navigator(Destination.Home),
            library = Library(MediaStoreLibrary(this), scope),
            search = Search(youtube, youtube, scope, SearchHistory(store, scope)),
            artists = Artists(youtube, scope),
            collections = Collections(youtube, scope),
            playlists = playlists,
            favorites = favorites,
            history = history,
            playback = playback,
            radio = Radio(youtube, playback, preferences.autoplay, hidden.ids, scope),
            // YouTube Music first: its timed lines follow the very recording that plays. LRCLIB
            // covers what it lacks, and the files on the phone, which YouTube Music does not know.
            lyrics = SongLyrics(FirstLyrics(listOf(youtube, Lrclib(versionName()))), playback, store, scope),
            explore = Explore(youtube, youtube, scope),
            home = Home(youtube, youtube, history, favorites, hidden.ids, scope),
            recentSources = RecentSources(store, playlists, scope),
            moods = Moods(youtube, scope),
            playlistImport = PlaylistImport(youtube, listOf(SpotifyPlaylists(versionName())), youtube, playlists, scope),
            downloads = downloads,
            hidden = hidden,
            bugReport = BugReport(versionName(), scope),
            links = youtube,
            preferences = preferences,
            account = account,
        )
        followAccount(account)
    }

    /**
     * Reloads home and explore when the account signs in or out, since both follow it, and drops
     * the open albums and playlists, which may be the account's private ones.
     */
    private fun followAccount(account: Account) {
        scope.launch {
            account.status.map { it is AccountStatus.SignedIn }.distinctUntilChanged().drop(1).collect {
                state.collections.clear()
                state.home.refresh()
                state.explore.refresh()
            }
        }
    }

    /** Tells [offline] whether a screen of Hihu is showing, by counting the activities started. */
    private fun watchVisible() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var started = 0
            override fun onActivityStarted(activity: Activity) {
                started++
                offline.setVisible(true)
            }
            override fun onActivityStopped(activity: Activity) {
                started = (started - 1).coerceAtLeast(0)
                if (started == 0) offline.setVisible(false)
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    /** Tells [likedDownloads] whenever a network that costs nothing per byte, like Wi-Fi, connects. */
    private fun watchUnmetered(connectivity: ConnectivityManager, likedDownloads: LikedDownloads) {
        val unmetered = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            .build()
        connectivity.registerNetworkCallback(unmetered, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch { likedDownloads.onUnmetered() }
            }
        })
    }

    private fun versionName(): String =
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "0"

    /**
     * The language of the phone, with the country of the SIM, then of the network, then of the
     * time zone, then of the phone's own setting. A phone set to English in Indonesia should
     * still get Indonesia's charts, and with two SIMs or in airplane mode the first two can be
     * empty. None of these needs a permission.
     */
    private fun contentLocale(): Locale {
        val telephony = getSystemService(TelephonyManager::class.java)
        val zone = runCatching { android.icu.util.TimeZone.getRegion(java.util.TimeZone.getDefault().id) }.getOrNull()
        val country = listOfNotNull(telephony?.simCountryIso, telephony?.networkCountryIso, zone)
            .firstOrNull { it.length == 2 && it != NO_REGION }
            ?.uppercase(Locale.ROOT)
            ?: Locale.getDefault().country
        return runCatching { Locale.Builder().setLanguage(Locale.getDefault().language).setRegion(country).build() }
            .getOrDefault(Locale.getDefault())
    }

    private companion object {
        const val TAG = "Hihu"

        /** What ICU answers for a time zone that belongs to no country, like UTC. */
        const val NO_REGION = "001"
    }
}
