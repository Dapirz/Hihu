package app.hihu.state

import android.util.Log
import app.hihu.music.Song
import app.hihu.music.SongLibrary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the library screen shows: the songs read so far and whether a read is running or failed. */
data class LibraryState(
    val songs: List<Song> = emptyList(),
    val loading: Boolean = false,
    val failed: Boolean = false,
    val loaded: Boolean = false,
)

/**
 * Holds the songs from a [SongLibrary] for the whole app. Views read [state] and call [refresh];
 * they never query the library themselves.
 */
class Library(private val source: SongLibrary, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private var load: Job? = null

    /** Reads the library again, cancelling a read that is still running. */
    fun refresh() {
        load?.cancel()
        load = scope.launch {
            _state.update { it.copy(loading = true, failed = false) }
            try {
                val songs = source.songs()
                _state.update { LibraryState(songs = songs, loaded = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "library: cannot read songs", e)
                _state.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    private companion object {
        const val TAG = "Hihu"
    }
}
