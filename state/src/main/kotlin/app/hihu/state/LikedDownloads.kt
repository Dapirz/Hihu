package app.hihu.state

import app.hihu.music.Song
import kotlinx.coroutines.flow.StateFlow

/**
 * Downloads each song the user hearts, when [enabled] is on, but only over Wi-Fi or another
 * unmetered network. A heart given on mobile data waits in [Preferences] until [onUnmetered] says
 * such a network is back. Only hearts given while it is on count, so turning it on does not
 * download every favourite at once.
 */
class LikedDownloads(
    private val enabled: StateFlow<Boolean>,
    private val preferences: Preferences,
    private val downloads: Downloads,
    private val favorites: () -> List<Song>,
    /** Whether the network in use costs nothing per byte, read when a heart is given. */
    private val unmetered: () -> Boolean,
) {
    /** Called for every heart given or taken away, after it is saved. */
    fun toggled(song: Song, liked: Boolean) {
        if (!liked) return preferences.setPendingDownloads(preferences.pendingDownloads() - song.id)
        if (!enabled.value || !downloads.supports(song)) return
        when (unmetered()) {
            true -> downloads.download(listOf(song))
            false -> preferences.setPendingDownloads(preferences.pendingDownloads() + song.id)
        }
    }

    /** Called when an unmetered network connects: downloads the hearts that were waiting for one. */
    fun onUnmetered() {
        val waiting = preferences.pendingDownloads()
        if (waiting.isEmpty()) return
        preferences.setPendingDownloads(emptySet())
        if (!enabled.value) return
        val songs = favorites().filter { it.id in waiting }
        if (songs.isNotEmpty()) downloads.download(songs)
    }
}
