package app.hihu.views.chrome

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import app.hihu.ui.HihuTheme
import app.hihu.ui.Icons
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hihu.music.Song
import app.hihu.state.Artists
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
    /** Finds the names credited on a song. */
    val artists: Artists,
    /** Opens the page of the artist with this name, from wherever the app is. */
    val openArtist: (String) -> Unit,
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
    ArtistItems(song, dismiss)
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

/** "Go to <artist>", one for each artist credited on [song], opening their page. */
@Composable
fun ArtistItems(song: Song, dismiss: () -> Unit) {
    val actions = LocalSongActions.current
    actions.artists.names(song).forEach { name ->
        DropdownMenuItem(
            text = { Text(stringResource(R.string.go_to_artist, name)) },
            onClick = {
                dismiss()
                actions.openArtist(name)
            },
        )
    }
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

/**
 * The bar over a list while several songs are chosen: how many, and what to do with them all at
 * once. Each action ends the choosing through [done].
 */
@Composable
fun SelectionBar(songs: List<Song>, done: () -> Unit) {
    val m = HihuTheme.metrics
    val actions = LocalSongActions.current
    val downloadable = songs.filter(actions.downloads::supports)
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = m.gapXs)) {
            IconButton(onClick = done) {
                Icon(painterResource(Icons.close), stringResource(R.string.select_done), Modifier.size(m.iconMd))
            }
            Text(
                pluralStringResource(R.plurals.selected_count, songs.size, songs.size),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(start = m.gapXs),
            )
            IconButton(onClick = {
                actions.playback.playNext(songs)
                done()
            }) { Icon(painterResource(Icons.queue), stringResource(R.string.play_next), Modifier.size(m.iconMd)) }
            IconButton(onClick = {
                done()
                actions.pickPlaylist(songs)
            }) { Icon(painterResource(Icons.add), stringResource(R.string.add_to_playlist), Modifier.size(m.iconMd)) }
            IconButton(onClick = {
                actions.download(downloadable)
                done()
            }, enabled = downloadable.isNotEmpty()) { Icon(painterResource(Icons.download), stringResource(R.string.download), Modifier.size(m.iconMd)) }
            IconButton(onClick = {
                songs.forEach(actions.hidden::hide)
                done()
            }) { Icon(painterResource(Icons.hide), stringResource(R.string.hide_song), Modifier.size(m.iconMd)) }
        }
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
