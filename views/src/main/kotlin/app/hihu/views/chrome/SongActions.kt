package app.hihu.views.chrome

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hihu.music.Song
import app.hihu.state.DownloadStatus
import app.hihu.state.Downloads
import app.hihu.state.HiddenSongs
import app.hihu.state.Favorites
import app.hihu.state.Playback
import app.hihu.state.Radio
import app.hihu.music.PlaylistLinks
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import app.hihu.views.R

/**
 * What can be done to any song from its menu. [pickPlaylist] opens the playlist picker, which
 * the root shell hosts. Screens reach this through [LocalSongActions].
 */
class SongActions(
    val playback: Playback,
    val favorites: Favorites,
    val downloads: Downloads,
    val hidden: HiddenSongs,
    val radio: Radio,
    val links: PlaylistLinks,
    val pickPlaylist: (List<Song>) -> Unit,
    /** Starts downloading, asking for the notification permission first when it is missing. */
    val download: (List<Song>) -> Unit,
)

val LocalSongActions = staticCompositionLocalOf<SongActions> { error("SongActions is not provided") }

/**
 * The menu items every song has: play next, queue, start a radio, favourite, add to playlist,
 * share, download and hide; the ones a song cannot use are left out.
 */
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
    RadioItem(song, dismiss)
    MenuItem(if (song.id in favorites) R.string.favorite_remove else R.string.favorite_add) {
        actions.favorites.toggle(song)
        dismiss()
    }
    MenuItem(R.string.add_to_playlist) {
        dismiss()
        actions.pickPlaylist(listOf(song))
    }
    ShareItem(song, dismiss)
    DownloadItems(song, dismiss)
    HideItem(song, dismiss)
}

/** "Start radio": [song] now, then songs like it without end, for a song that has a radio. */
@Composable
fun RadioItem(song: Song, dismiss: () -> Unit) {
    val actions = LocalSongActions.current
    if (!actions.radio.hasRadio(song)) return
    MenuItem(R.string.start_radio) {
        actions.radio.start(song)
        dismiss()
    }
}

/** "Share": the song's title, artist and link through Android's share sheet, for a song with a link. */
@Composable
fun ShareItem(song: Song, dismiss: () -> Unit) {
    val actions = LocalSongActions.current
    val link = actions.links.songLink(song) ?: return
    val context = LocalContext.current
    val text = stringResource(R.string.share_song_text, song.title, song.artist ?: stringResource(R.string.unknown_artist), link)
    val title = stringResource(R.string.share_song)
    MenuItem(R.string.share_song) {
        dismiss()
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, title))
    }
}

/** "Don't play this again": the song leaves the queue and never comes up in a radio again. */
@Composable
fun HideItem(song: Song, dismiss: () -> Unit) {
    val actions = LocalSongActions.current
    MenuItem(R.string.hide_song) {
        actions.hidden.hide(song)
        dismiss()
    }
}

/** Download, download again or remove the download, for a song its provider can download. */
@Composable
fun DownloadItems(song: Song, dismiss: () -> Unit) {
    val actions = LocalSongActions.current
    if (actions.downloads.supports(song)) {
        val downloaded by actions.downloads.progress.collectAsStateWithLifecycle()
        if (downloaded[song.id]?.status == DownloadStatus.Failed) {
            MenuItem(R.string.download_retry) {
                actions.download(listOf(song))
                dismiss()
            }
        }
        when (song.id in downloaded) {
            true -> MenuItem(R.string.download_remove) {
                actions.downloads.remove(song)
                dismiss()
            }
            false -> MenuItem(R.string.download) {
                actions.download(listOf(song))
                dismiss()
            }
        }
    }
}

/** One text item of a dropdown menu. */
@Composable
fun MenuItem(label: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = onClick)
}
