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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hihu.views.shared.SkeletonShelves
import app.hihu.music.Collection
import app.hihu.music.Mood
import app.hihu.music.Song
import app.hihu.state.Explore
import app.hihu.state.NowPlaying
import app.hihu.state.Playback
import app.hihu.ui.HihuTheme
import app.hihu.ui.Icons
import app.hihu.views.R
import app.hihu.views.chrome.CommonSongItems
import app.hihu.views.shared.MoodGrid
import app.hihu.views.shared.ScreenTitle
import app.hihu.views.shared.ShelfTitle
import app.hihu.views.shared.ShelfView
import app.hihu.views.shared.SongRow
import java.util.Locale

/**
 * The Explore tab, like YouTube Music's: moods and genres, new albums and videos, and the
 * trending chart for the user's country from YouTube Music's charts page, numbered. No account needed.
 */
@Composable
fun ExploreScreen(
    explore: Explore,
    playback: Playback,
    now: NowPlaying,
    contentPadding: PaddingValues,
    onSongs: (Song, List<Song>) -> Unit,
    onOpen: (Collection) -> Unit,
    onMood: (Mood) -> Unit,
    onSettings: () -> Unit,
) {
    val m = HihuTheme.metrics
    val page by explore.page.collectAsStateWithLifecycle()
    val trending by explore.trending.collectAsStateWithLifecycle()
    val country by explore.country.collectAsStateWithLifecycle()

    LaunchedEffect(explore) { explore.open() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenTitle(stringResource(R.string.tab_explore), onSettings = onSettings, modifier = Modifier.padding(vertical = m.titleGap))
        LazyColumn(contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())) {
            val feed = page.feed
            when {
                feed != null -> {
                    if (feed.moods.isNotEmpty()) item(key = "moods") {
                        Column(Modifier.padding(bottom = m.gapLg)) {
                            ShelfTitle(stringResource(R.string.moods_title))
                            MoodGrid(feed.moods, onMood)
                        }
                    }
                    // YouTube Music's own trending list is left out: the chart below is the same.
                    // Two parts of a feed can share a title, and a title is each row's key.
                    items(feed.shelves.filterNot { it.ranked }.distinctBy { it.title }, key = { "shelf:${it.title}" }) { shelf ->
                        ShelfView(shelf.title, shelf.items, onSongs, onOpen, menu = { song, _, dismiss -> CommonSongItems(song, dismiss) })
                    }
                }
                page.loading -> item(key = "loading") {
                    SkeletonShelves()
                }
                page.failed -> item(key = "failed") { Retry(stringResource(R.string.explore_feed_failed), explore::refresh) }
            }
            if (trending.items.isNotEmpty()) {
                item(key = "chart") { ChartHeader(country, trending.items, playback) }
                itemsIndexed(trending.items, key = { _, song -> "chart:${song.id}" }) { index, song ->
                    SongRow(
                        song,
                        playing = song.id == now.songId,
                        onClick = { playback.play(trending.items, index) },
                        menu = { dismiss -> CommonSongItems(song, dismiss) },
                        rank = index + 1,
                    )
                }
            } else if (trending.failed) {
                item(key = "chart-failed") { Retry(stringResource(R.string.explore_failed), explore::refresh) }
            }
        }
    }
}

@Composable
private fun Retry(text: String, onRetry: () -> Unit) {
    val m = HihuTheme.metrics
    Column(Modifier.fillMaxWidth().padding(m.gapXl), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

/** "Trending in Indonesia" with Shuffle and Play all for the whole chart. */
@Composable
private fun ChartHeader(country: String?, songs: List<Song>, playback: Playback) {
    val m = HihuTheme.metrics
    val name = country?.let { Locale.Builder().setRegion(it).build().displayCountry }.orEmpty()
    val buttons = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(m.gapSm),
        modifier = Modifier.fillMaxWidth().padding(start = m.gapXl, end = m.gapLg, top = m.gapSm, bottom = m.gapSm),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.trending_in, name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.trending_source),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalIconButton(onClick = { playback.playAll(songs, shuffle = true) }, colors = buttons, modifier = Modifier.size(m.roundButton)) {
            Icon(painterResource(Icons.shuffle), stringResource(R.string.shuffle_all), Modifier.size(m.iconMd))
        }
        FilledTonalIconButton(onClick = { playback.playAll(songs, shuffle = false) }, colors = buttons, modifier = Modifier.size(m.roundButton)) {
            Icon(painterResource(Icons.play), stringResource(R.string.play_all), Modifier.size(m.iconMd))
        }
    }
}
