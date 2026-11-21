package app.hihu.views.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hihu.music.CollectionKind
import app.hihu.music.Song
import app.hihu.state.NowPlaying
import app.hihu.state.OpenCollection
import app.hihu.state.Playback
import app.hihu.state.Radio
import app.hihu.ui.HihuTheme
import app.hihu.ui.Icons
import app.hihu.views.chrome.SelectionBar
import app.hihu.views.R
import app.hihu.views.shared.ButtonLabel
import app.hihu.views.shared.HeaderButtons
import app.hihu.views.chrome.CommonSongItems
import app.hihu.views.shared.Artwork
import app.hihu.views.shared.Message
import app.hihu.views.shared.SongList
import app.hihu.views.shared.collectionSubtitle
import app.hihu.views.shared.more

/**
 * An album or artist: the cover, title and Play all / Shuffle above its songs, which keep
 * loading as the list is scrolled. A mix plays through [radio], so it keeps going after the
 * songs shown here.
 */
@Composable
fun CollectionScreen(
    open: OpenCollection,
    playback: Playback,
    radio: Radio,
    now: NowPlaying,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val m = HihuTheme.metrics
    val songs by open.songs.collectAsStateWithLifecycle()
    val padding = PaddingValues(bottom = contentPadding.calculateBottomPadding())
    val mix = remember(open.collection) { radio.isMix(open.collection) }
    val radioLoading by radio.startingArtist.collectAsStateWithLifecycle()
    val artistRadio = remember(open.collection) {
        { radio.startArtist(open.collection) }.takeIf { radio.hasArtistRadio(open.collection) }
    }
    // A null start is Play all, which also turns shuffle off.
    val play = { list: List<Song>, start: Int?, shuffle: Boolean ->
        when {
            mix -> radio.playMix(open.collection, list, start ?: 0, shuffle)
            shuffle || start == null -> playback.playAll(list, shuffle)
            else -> playback.play(list, start)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = m.gapXs)) {
            IconButton(onClick = onBack) {
                Icon(painterResource(Icons.back), stringResource(R.string.back), Modifier.size(m.iconMd))
            }
        }
        when {
            songs.loading -> Column {
                Header(open, songs = emptyList(), total = null, play = play, onRadio = artistRadio, radioLoading = radioLoading)
                Box(Modifier.fillMaxWidth().padding(m.gapXl), Alignment.Center) { CircularProgressIndicator() }
            }
            songs.failed -> Message(stringResource(R.string.collection_failed), stringResource(R.string.retry), open::retry)
            songs.items.isEmpty() -> Message(stringResource(R.string.collection_empty))
            else -> SongList(
                songs = songs.items,
                playingId = now.songId,
                padding = padding,
                onPlay = { index -> play(songs.items, index, false) },
                more = songs.more(open::loadMore),
                header = { Header(open, songs.items, songs.total, play, artistRadio, radioLoading) },
                selectionBar = { chosen, done -> SelectionBar(chosen, done) },
                menu = { song, _, dismiss -> CommonSongItems(song, dismiss) },
            )
        }
    }
}

@Composable
private fun Header(
    open: OpenCollection,
    songs: List<Song>,
    total: Long?,
    play: (List<Song>, Int?, Boolean) -> Unit,
    onRadio: (() -> Unit)? = null,
    radioLoading: Boolean = false,
) {
    val m = HihuTheme.metrics
    // An album's length is only known once its page is open; an artist's count is subscribers.
    val collection = when (open.collection.kind) {
        CollectionKind.Album, CollectionKind.Playlist -> open.collection.copy(count = total ?: open.collection.count)
        CollectionKind.Artist -> open.collection
    }
    val radius = when (collection.kind) {
        CollectionKind.Album, CollectionKind.Playlist -> m.radiusLg
        CollectionKind.Artist -> m.collectionArtwork / 2
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(horizontal = m.gapXl, vertical = m.gapLg),
    ) {
        Artwork(collection.artworkUri, m.collectionArtwork, radius = radius)
        Text(
            collection.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = m.gapLg),
        )
        collectionSubtitle(collection)?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = m.gapXs),
            )
        }
        HeaderButtons(Modifier.padding(top = m.gapLg)) {
            Button(onClick = { play(songs, null, false) }, enabled = songs.isNotEmpty()) {
                Icon(painterResource(Icons.play), contentDescription = null, Modifier.size(m.iconSm))
                ButtonLabel(stringResource(R.string.play_all))
            }
            FilledTonalButton(onClick = { play(songs, null, true) }, enabled = songs.isNotEmpty()) {
                Icon(painterResource(Icons.shuffle), contentDescription = null, Modifier.size(m.iconSm))
                ButtonLabel(stringResource(R.string.shuffle))
            }
            if (onRadio != null) {
                val label = stringResource(R.string.artist_radio_label)
                // The radio's first page takes a moment; a spinner in place of the icon shows it is coming.
                FilledTonalButton(
                    onClick = onRadio,
                    enabled = !radioLoading,
                    modifier = Modifier.semantics { contentDescription = label },
                ) {
                    when (radioLoading) {
                        true -> CircularProgressIndicator(Modifier.size(m.iconSm), strokeWidth = m.spinnerStroke)
                        false -> Icon(painterResource(Icons.radio), contentDescription = null, Modifier.size(m.iconSm))
                    }
                    ButtonLabel(stringResource(R.string.artist_radio))
                }
            }
        }
    }
}
