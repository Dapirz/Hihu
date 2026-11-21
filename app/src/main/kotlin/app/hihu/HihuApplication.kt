package app.hihu

import android.app.Application
import android.content.ComponentName
import android.net.ConnectivityManager
import android.telephony.TelephonyManager
import android.util.Log
import java.util.Locale
import app.hihu.music.local.MediaStoreLibrary
import app.hihu.music.youtube.YouTubeMusic
import app.hihu.router.Destination
import app.hihu.router.Navigator
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
        )
        val store = Store(this)
        val history = History(store, scope)
        // Signed in, a heart is also a like on the account, sent by AccountLikes once it exists.
        lateinit var likes: AccountLikes
        val favorites = Favorites(store, scope) { song, liked -> likes.toggled(song, liked) }
        normalizer = Normalizer(this, youtube, preferences.normalize, scope)
        offline = Offline(this, listOf(youtube), onDownload = normalizer::prefetch)
        val playback = Playback(
            context = this,
            service = ComponentName(this, PlaybackService::class.java),
            scope = scope,
            saved = SavedQueue(store),
            onSongStarted = history::record,
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
        state = AppState(
            navigator = Navigator(Destination.Home),
            library = Library(MediaStoreLibrary(this), scope),
            search = Search(youtube, scope, SearchHistory(store, scope)),
            collections = Collections(youtube, scope),
            playlists = playlists,
            favorites = favorites,
            history = history,
            playback = playback,
            radio = Radio(youtube, playback, preferences.autoplay, hidden.ids, scope),
            // LRCLIB first, for its synced lines; YouTube Music has plain lyrics for many songs LRCLIB lacks.
            lyrics = SongLyrics(FirstLyrics(listOf(Lrclib(versionName()), youtube)), playback, store, scope),
            explore = Explore(youtube, youtube, scope),
            home = Home(youtube, youtube, history, favorites, hidden.ids, scope),
            moods = Moods(youtube, scope),
            playlistImport = PlaylistImport(youtube, playlists, scope),
            downloads = Downloads(offline, store, scope),
            hidden = hidden,
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
