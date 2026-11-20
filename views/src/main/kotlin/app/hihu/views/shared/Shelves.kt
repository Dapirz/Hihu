package app.hihu.views.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.hihu.music.Collection
import app.hihu.music.CollectionKind
import app.hihu.music.Mood
import app.hihu.music.SearchResult
import app.hihu.music.Song
import app.hihu.ui.HihuTheme

/**
 * A titled shelf. Songs show as a sideways-scrolling grid of up to four rows, like YouTube Music's
 * quick picks; albums, playlists and artists as a row of cards. [onSong] gets the song tapped and
 * every song of the shelf.
 */
@Composable
fun ShelfView(
    title: String,
    items: List<SearchResult>,
    onSong: (Song, List<Song>) -> Unit,
    onOpen: (Collection) -> Unit,
    menu: SongMenu? = null,
) {
    val m = HihuTheme.metrics
    val songs = items.mapNotNull { (it as? SearchResult.Track)?.song }
    Column(Modifier.fillMaxWidth().padding(bottom = m.gapLg)) {
        ShelfTitle(title)
        when (songs.size == items.size) {
            true -> SongGrid(songs, onSong, menu)
            false -> CardRow(items, songs, onSong, onOpen)
        }
    }
}

/** The heading over a shelf. */
@Composable
fun ShelfTitle(title: String) {
    val m = HihuTheme.metrics
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = m.gapXl, vertical = m.gapSm),
    )
}

@Composable
private fun SongGrid(songs: List<Song>, onSong: (Song, List<Song>) -> Unit, menu: SongMenu?) {
    val m = HihuTheme.metrics
    val rows = songs.size.coerceAtMost(GRID_ROWS)
    // Each column is most of the screen wide, so the next column peeks in to show it scrolls.
    val column = LocalConfiguration.current.screenWidthDp.dp * COLUMN_FRACTION
    LazyHorizontalGrid(
        rows = GridCells.Fixed(rows),
        contentPadding = PaddingValues(horizontal = m.gapSm),
        modifier = Modifier.fillMaxWidth().height(m.songRowHeight * rows),
    ) {
        itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
            Box(Modifier.width(column)) {
                SongRow(
                    song,
                    playing = false,
                    onClick = { onSong(song, songs) },
                    menu = menu?.let { items -> { dismiss -> items(song, index, dismiss) } },
                )
            }
        }
    }
}

@Composable
private fun CardRow(items: List<SearchResult>, songs: List<Song>, onSong: (Song, List<Song>) -> Unit, onOpen: (Collection) -> Unit) {
    val m = HihuTheme.metrics
    LazyRow(contentPadding = PaddingValues(horizontal = m.gapLg), horizontalArrangement = Arrangement.spacedBy(m.gapMd)) {
        items(items, key = { it.key }) { item ->
            when (item) {
                is SearchResult.Group -> Card(
                    title = item.collection.title,
                    subtitle = collectionSubtitle(item.collection),
                    artworkUri = item.collection.artworkUri,
                    round = item.collection.kind == CollectionKind.Artist,
                    onClick = { onOpen(item.collection) },
                )
                is SearchResult.Track -> Card(
                    title = item.song.title,
                    subtitle = item.song.artist,
                    artworkUri = item.song.artworkUri,
                    round = false,
                    onClick = { onSong(item.song, songs) },
                )
            }
        }
    }
}

/** A square cover (round for an artist) with a title of up to two lines and a subtitle. */
@Composable
private fun Card(title: String, subtitle: String?, artworkUri: String?, round: Boolean, onClick: () -> Unit) {
    val m = HihuTheme.metrics
    Column(Modifier.width(m.cardWidth).clip(RoundedCornerShape(m.radiusMd)).clickable(onClick = onClick)) {
        Artwork(artworkUri, m.cardWidth, radius = if (round) m.cardWidth / 2 else m.radiusMd)
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = m.gapSm),
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Moods and genres as two sideways-scrolling rows of buttons with a coloured edge. */
@Composable
fun MoodGrid(moods: List<Mood>, onOpen: (Mood) -> Unit) {
    val m = HihuTheme.metrics
    LazyHorizontalGrid(
        rows = GridCells.Fixed(MOOD_ROWS),
        contentPadding = PaddingValues(horizontal = m.gapLg),
        horizontalArrangement = Arrangement.spacedBy(m.gapSm),
        verticalArrangement = Arrangement.spacedBy(m.gapSm),
        modifier = Modifier.fillMaxWidth().height(m.moodHeight * MOOD_ROWS + m.gapSm),
    ) {
        itemsIndexed(moods, key = { _, mood -> mood.ref }) { _, mood ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(m.radiusSm),
                modifier = Modifier.width(m.moodWidth).height(m.moodHeight).clickable { onOpen(mood) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The stripe colour comes from YouTube Music, like its own buttons.
                    Box(
                        Modifier.width(m.moodStripe).fillMaxHeight()
                            .background(mood.color?.let { Color(it.toInt()) } ?: MaterialTheme.colorScheme.primary),
                    )
                    Text(
                        mood.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = m.gapMd),
                    )
                }
            }
        }
    }
}

private const val GRID_ROWS = 4
private const val MOOD_ROWS = 2
private const val COLUMN_FRACTION = 0.86f
