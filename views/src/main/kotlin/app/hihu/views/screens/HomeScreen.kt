package app.hihu.views.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hihu.music.Collection
import app.hihu.music.SearchResult
import app.hihu.music.Song
import app.hihu.state.Home
import app.hihu.state.PersonalRow
import app.hihu.ui.HihuTheme
import app.hihu.views.R
import app.hihu.views.chrome.CommonSongItems
import app.hihu.views.shared.LoadMoreNearEnd
import app.hihu.views.shared.ScreenTitle
import app.hihu.views.shared.ShelfView
import app.hihu.views.shared.more
import app.hihu.views.shared.moreFooter

/**
 * The Home tab: rows made from Hihu's own history and favourites first, then YouTube Music's
 * home shelves, which keep coming as the list scrolls. Nothing here needs an account.
 */
@Composable
fun HomeScreen(
    home: Home,
    contentPadding: PaddingValues,
    onSongs: (Song, List<Song>) -> Unit,
    onOpen: (Collection) -> Unit,
    onSettings: () -> Unit,
) {
    val m = HihuTheme.metrics
    val personal by home.personal.collectAsStateWithLifecycle()
    val releases by home.releases.collectAsStateWithLifecycle()
    val shelves by home.shelves.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    val more = shelves.more(home::loadMore)

    LaunchedEffect(home) { home.open() }
    LoadMoreNearEnd(list, more)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenTitle(stringResource(R.string.brand), onSettings = onSettings, modifier = Modifier.padding(vertical = m.gapLg))
        LazyColumn(state = list, contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())) {
            items(personal, key = { it::class.simpleName.orEmpty() }) { row ->
                val title = when (row) {
                    is PersonalRow.KeepListening -> stringResource(R.string.home_keep_listening)
                    is PersonalRow.MostPlayed -> stringResource(R.string.home_most_played)
                    is PersonalRow.Forgotten -> stringResource(R.string.home_forgotten)
                    is PersonalRow.Similar -> stringResource(R.string.home_similar, row.artist)
                }
                ShelfView(
                    title = title,
                    items = row.songs.map(SearchResult::Track),
                    onSong = onSongs,
                    onOpen = onOpen,
                    menu = { song, _, dismiss -> CommonSongItems(song, dismiss) },
                )
            }
            if (releases.isNotEmpty()) {
                item(key = "releases") {
                    ShelfView(
                        title = stringResource(R.string.home_new_releases),
                        items = releases.map(SearchResult::Group),
                        onSong = onSongs,
                        onOpen = onOpen,
                    )
                }
            }
            items(shelves.items, key = { "shelf:${it.title}" }) { shelf ->
                ShelfView(
                    title = shelf.title,
                    items = shelf.items,
                    onSong = onSongs,
                    onOpen = onOpen,
                    menu = { song, _, dismiss -> CommonSongItems(song, dismiss) },
                )
            }
            when {
                shelves.loading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(m.gapXl), Alignment.Center) { CircularProgressIndicator() }
                }
                shelves.failed -> item(key = "failed") {
                    Column(Modifier.fillMaxWidth().padding(m.gapXl), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.home_failed))
                        TextButton(onClick = home::refresh) { Text(stringResource(R.string.retry)) }
                    }
                }
            }
            moreFooter(more)
        }
    }
}
