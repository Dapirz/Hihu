package com.daffa.hihu

import android.app.Application
import android.content.ComponentName
import android.telephony.TelephonyManager
import java.util.Locale
import com.daffa.hihu.music.local.MediaStoreLibrary
import com.daffa.hihu.music.youtube.YouTubeMusic
import com.daffa.hihu.router.Destination
import com.daffa.hihu.router.Navigator
import com.daffa.hihu.state.Collections
import com.daffa.hihu.state.Downloads
import com.daffa.hihu.state.Explore
import com.daffa.hihu.state.Favorites
import com.daffa.hihu.state.History
import com.daffa.hihu.state.Home
import com.daffa.hihu.state.Moods
import com.daffa.hihu.state.Library
import com.daffa.hihu.state.Playback
import com.daffa.hihu.state.PlaylistImport
import com.daffa.hihu.state.Playlists
import com.daffa.hihu.state.Preferences
import com.daffa.hihu.state.Radio
import com.daffa.hihu.state.SavedQueue
import com.daffa.hihu.state.Search
import com.daffa.hihu.state.SearchHistory
import com.daffa.hihu.state.SongLyrics
import com.daffa.hihu.music.lrclib.Lrclib
import com.daffa.hihu.state.Store
import com.daffa.hihu.views.shells.AppState
import kotlinx.coroutines.MainScope

/**
 * The composition root. This is the only place that names a concrete provider; everything else
 * sees the interfaces in the music module. State lives here so it outlives the activity.
 */
class HihuApplication : Application() {
    private val scope = MainScope()

    lateinit var state: AppState
        private set

    /** Downloads, and the data source the player reads through. One per process. */
    lateinit var offline: Offline
        private set

    override fun onCreate() {
        super.onCreate()
        val preferences = Preferences(this)
        val youtube = YouTubeMusic(locale = contentLocale(), quality = { preferences.quality.value })
        val store = Store(this)
        val history = History(store, scope)
        val favorites = Favorites(store, scope)
        offline = Offline(this, listOf(youtube))
        val playback = Playback(
            context = this,
            service = ComponentName(this, PlaybackService::class.java),
            scope = scope,
            saved = SavedQueue(store),
            onSongStarted = history::record,
        )
        val playlists = Playlists(store, scope)
        state = AppState(
            navigator = Navigator(Destination.Home),
            library = Library(MediaStoreLibrary(this), scope),
            search = Search(youtube, scope, SearchHistory(store, scope)),
            collections = Collections(youtube, scope),
            playlists = playlists,
            favorites = favorites,
            history = history,
            playback = playback,
            radio = Radio(youtube, playback, scope),
            lyrics = SongLyrics(Lrclib(), playback, scope),
            explore = Explore(youtube, youtube, scope),
            home = Home(youtube, youtube, history, favorites, scope),
            moods = Moods(youtube, scope),
            playlistImport = PlaylistImport(youtube, playlists, scope),
            downloads = Downloads(offline, store, scope),
            preferences = preferences,
        )
    }

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
        /** What ICU answers for a time zone that belongs to no country, like UTC. */
        const val NO_REGION = "001"
    }
}
