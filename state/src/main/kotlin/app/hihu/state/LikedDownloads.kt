package app.hihu.state

import app.hihu.music.Song
import kotlinx.coroutines.flow.StateFlow

/**
 * Downloads each song the user hearts, when [enabled] is on: right away on Wi-Fi or another
 * unmetered network, and on mobile data too when [mobileToo] is on. Otherwise a heart given on
 * mobile data waits in [Preferences] until [onUnmetered] says such a network is back. Only hearts
 * given while it is on count, so turning it on does not download every favourite at once.
 */
class LikedDownloads(
    private val enabled: StateFlow<Boolean>,
    private val mobileToo: StateFlow<Boolean>,
    private val preferences: Preferences,
    private val downloads: Downloads,
    /** The hearted songs, once they have been read; waits for that at start-up. */
    private val favorites: suspend () -> List<Song>,
    /** Whether the network in use costs nothing per byte, read when a heart is given. */
    private val unmetered: () -> Boolean,
) {
    /** Called for every heart given or taken away, after it is saved. */
    fun toggled(song: Song, liked: Boolean) {
        if (!liked) return preferences.setPendingDownloads(preferences.pendingDownloads() - song.id)
        if (!enabled.value || !downloads.supports(song)) return
        when (mobileToo.value || unmetered()) {
            true -> downloads.download(listOf(song))
            false -> preferences.setPendingDownloads(preferences.pendingDownloads() + song.id)
        }
    }

    /** Called when an unmetered network connects: downloads the hearts that were waiting for one. */
    suspend fun onUnmetered() {
        val waiting = preferences.pendingDownloads()
        if (waiting.isEmpty()) return
        if (!enabled.value) return preferences.setPendingDownloads(emptySet())
        val songs = favorites().filter { it.id in waiting }
        // Only what was found leaves the list; a heart taken away since is taken out by [toggled].
        preferences.setPendingDownloads(preferences.pendingDownloads() - songs.mapTo(HashSet(), Song::id))
        if (songs.isNotEmpty()) downloads.download(songs)
    }
}
