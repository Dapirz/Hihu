package com.daffa.hihu.views.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daffa.hihu.music.Collection
import com.daffa.hihu.music.Song
import com.daffa.hihu.state.OpenMood
import com.daffa.hihu.ui.HihuTheme
import com.daffa.hihu.ui.Icons
import com.daffa.hihu.views.R
import com.daffa.hihu.views.chrome.CommonSongItems
import com.daffa.hihu.views.shared.Message
import com.daffa.hihu.views.shared.ShelfView

/** One mood or genre: its shelves of playlists, each opening like an album. */
@Composable
fun MoodScreen(
    open: OpenMood,
    contentPadding: PaddingValues,
    onSongs: (Song, List<Song>) -> Unit,
    onOpen: (Collection) -> Unit,
    onBack: () -> Unit,
) {
    val m = HihuTheme.metrics
    val state by open.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = m.gapXs)) {
            IconButton(onClick = onBack) {
                Icon(painterResource(Icons.back), stringResource(R.string.back), Modifier.size(m.iconMd))
            }
            Text(
                open.mood.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = m.gapSm),
            )
        }
        when {
            state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            state.failed -> Message(stringResource(R.string.collection_failed), stringResource(R.string.retry), open.retry)
            else -> LazyColumn(contentPadding = PaddingValues(top = m.gapSm, bottom = contentPadding.calculateBottomPadding())) {
                items(state.shelves, key = { it.title }) { shelf ->
                    ShelfView(shelf.title, shelf.items, onSongs, onOpen, menu = { song, _, dismiss -> CommonSongItems(song, dismiss) })
                }
            }
        }
    }
}
