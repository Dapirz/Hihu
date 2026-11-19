package com.daffa.hihu

import android.app.Application
import android.content.ComponentName
import com.daffa.hihu.music.StreamResolver
import com.daffa.hihu.music.local.MediaStoreLibrary
import com.daffa.hihu.music.youtube.YouTubeMusic
import com.daffa.hihu.router.Destination
import com.daffa.hihu.router.Navigator
import com.daffa.hihu.state.Collections
import com.daffa.hihu.state.Favorites
import com.daffa.hihu.state.History
import com.daffa.hihu.state.Library
import com.daffa.hihu.state.Playback
import com.daffa.hihu.state.Playlists
import com.daffa.hihu.state.Preferences
import com.daffa.hihu.state.Radio
import com.daffa.hihu.state.SavedQueue
import com.daffa.hihu.state.Search
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

    /** Every provider whose songs need resolving before they play, for PlaybackService. */
    lateinit var resolvers: List<StreamResolver>
        private set

    override fun onCreate() {
        super.onCreate()
        val preferences = Preferences(this)
        val youtube = YouTubeMusic(quality = { preferences.quality.value })
        val store = Store(this)
        val history = History(store, scope)
        resolvers = listOf(youtube)
        val playback = Playback(
            context = this,
            service = ComponentName(this, PlaybackService::class.java),
            scope = scope,
            saved = SavedQueue(store),
            onSongStarted = history::record,
        )
        state = AppState(
            navigator = Navigator(Destination.Search),
            library = Library(MediaStoreLibrary(this), scope),
            search = Search(youtube, scope),
            collections = Collections(youtube, scope),
            playlists = Playlists(store, scope),
            favorites = Favorites(store, scope),
            history = history,
            playback = playback,
            radio = Radio(youtube, playback, scope),
            lyrics = SongLyrics(Lrclib(), playback, scope),
            preferences = preferences,
        )
    }
}
