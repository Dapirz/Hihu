package com.daffa.hihu.views.chrome

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daffa.hihu.music.Song
import com.daffa.hihu.state.Favorites
import com.daffa.hihu.state.Playback
import com.daffa.hihu.views.R

/**
 * What can be done to any song from its menu. [pickPlaylist] opens the playlist picker, which
 * the root shell hosts. Screens reach this through [LocalSongActions].
 */
class SongActions(
    val playback: Playback,
    val favorites: Favorites,
    val pickPlaylist: (List<Song>) -> Unit,
)

val LocalSongActions = staticCompositionLocalOf<SongActions> { error("SongActions is not provided") }

/** The menu items every song has: play next, queue, favourite and add to playlist. */
@Composable
fun ColumnScope.CommonSongItems(song: Song, dismiss: () -> Unit) {
    val actions = LocalSongActions.current
    val favorites by actions.favorites.ids.collectAsStateWithLifecycle()
    MenuItem(R.string.play_next) {
        actions.playback.playNext(listOf(song))
        dismiss()
    }
    MenuItem(R.string.add_to_queue) {
        actions.playback.enqueue(listOf(song))
        dismiss()
    }
    MenuItem(if (song.id in favorites) R.string.favorite_remove else R.string.favorite_add) {
        actions.favorites.toggle(song)
        dismiss()
    }
    MenuItem(R.string.add_to_playlist) {
        dismiss()
        actions.pickPlaylist(listOf(song))
    }
}

/** One text item of a dropdown menu. */
@Composable
fun MenuItem(label: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = onClick)
}
