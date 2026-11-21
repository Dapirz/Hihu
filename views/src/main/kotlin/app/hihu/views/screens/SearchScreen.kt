package app.hihu.views.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hihu.music.Collection
import app.hihu.music.SearchKind
import app.hihu.music.SearchResult
import app.hihu.state.NowPlaying
import app.hihu.state.Playback
import app.hihu.state.Radio
import app.hihu.state.Search
import app.hihu.ui.HihuTheme
import app.hihu.ui.Icons
import app.hihu.views.chrome.SelectionBar
import app.hihu.views.R
import app.hihu.views.chrome.CommonSongItems
import app.hihu.views.shared.CollectionList
import app.hihu.views.shared.Message
import app.hihu.views.shared.ScreenTitle
import app.hihu.views.shared.SongList
import app.hihu.views.shared.more

/**
 * Searching YouTube Music: a rounded field, a row of kinds to search for, and the results on a
 * sheet. While the field has focus, suggestions for the typed text replace the results.
 */
@Composable
fun SearchScreen(
    search: Search,
    playback: Playback,
    radio: Radio,
    now: NowPlaying,
    contentPadding: PaddingValues,
    onOpen: (Collection) -> Unit,
    onSettings: () -> Unit,
) {
    val m = HihuTheme.metrics
    val query by search.query.collectAsStateWithLifecycle()
    val results by search.results.collectAsStateWithLifecycle()
    val recent by search.history.recent.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf(query.text) }
    var focused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val bottom = contentPadding.calculateBottomPadding()

    fun submit(value: String) {
        text = value
        keyboard?.hide()
        focus.clearFocus()
        search.submit(value)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenTitle(
            stringResource(R.string.tab_search),
            onSettings = onSettings,
            modifier = Modifier.padding(top = m.gapLg, bottom = m.gapMd),
        )
        SearchField(
            text = text,
            onTextChange = {
                text = it
                search.suggest(it)
            },
            onSubmit = { submit(text) },
            onClear = {
                text = ""
                search.suggest("")
                search.submit("")
            },
            modifier = Modifier
                .padding(horizontal = m.gapLg)
                .onFocusChanged { focused = it.isFocused },
        )
        KindChips(selected = query.kind, onSelect = search::select)
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(topStart = m.radiusSheet, topEnd = m.radiusSheet),
            modifier = Modifier.fillMaxSize(),
        ) {
            val listPadding = PaddingValues(top = m.gapSm, bottom = bottom)
            when {
                focused && query.suggestions.isNotEmpty() ->
                    Suggestions(query.suggestions, listPadding, onPick = ::submit)
                // With nothing typed, past searches stand in for the empty results.
                text.isEmpty() && recent.isNotEmpty() && (focused || !results.loaded) -> Recent(
                    queries = recent,
                    padding = listPadding,
                    onPick = ::submit,
                    onRemove = search.history::remove,
                    onClear = search.history::clear,
                )
                results.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                results.failed -> Message(stringResource(R.string.search_failed), stringResource(R.string.retry), search::retry)
                !results.loaded -> Message(stringResource(R.string.search_idle))
                results.items.isEmpty() -> Message(stringResource(R.string.search_empty, query.text))
                query.kind == SearchKind.Songs || query.kind == SearchKind.Videos -> {
                    val songs = results.items.mapNotNull { (it as? SearchResult.Track)?.song }
                    SongList(
                        songs = songs,
                        playingId = now.songId,
                        padding = listPadding,
                        // Like YouTube Music: the tapped song, then songs like it, not the other hits.
                        onPlay = { index -> radio.start(songs[index]) },
                        more = results.more(search::loadMore),
                        selectionBar = { chosen, done -> SelectionBar(chosen, done) },
                        menu = { song, _, dismiss -> CommonSongItems(song, dismiss) },
                    )
                }
                else -> CollectionList(
                    collections = results.items.mapNotNull { (it as? SearchResult.Group)?.collection },
                    padding = listPadding,
                    onOpen = onOpen,
                    more = results.more(search::loadMore),
                )
            }
        }
    }
}

/** One chip per kind of search, the selected one filled. */
@Composable
private fun KindChips(selected: SearchKind, onSelect: (SearchKind) -> Unit) {
    val m = HihuTheme.metrics
    val label = stringResource(R.string.search_kind)
    Row(
        horizontalArrangement = Arrangement.spacedBy(m.gapSm),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .semantics { contentDescription = label }
            .padding(horizontal = m.gapLg, vertical = m.gapMd),
    ) {
        SearchKind.entries.forEach { kind ->
            val name = when (kind) {
                SearchKind.Songs -> R.string.kind_songs
                SearchKind.Videos -> R.string.kind_videos
                SearchKind.Albums -> R.string.kind_albums
                SearchKind.Artists -> R.string.kind_artists
            }
            FilterChip(
                selected = kind == selected,
                onClick = { onSelect(kind) },
                label = { Text(stringResource(name)) },
                shape = RoundedCornerShape(m.radiusPill),
            )
        }
    }
}

@Composable
private fun Suggestions(suggestions: List<String>, padding: PaddingValues, onPick: (String) -> Unit) {
    val m = HihuTheme.metrics
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        items(suggestions, key = { it }) { suggestion ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(suggestion) }
                    .padding(horizontal = m.gapXl, vertical = m.gapMd),
            ) {
                Icon(
                    painterResource(Icons.search),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(m.iconMd),
                )
                Text(
                    suggestion,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = m.gapLg),
                )
            }
        }
    }
}

/** Past searches, newest first: tap one to search it again, or remove it or all of them. */
@Composable
private fun Recent(
    queries: List<String>,
    padding: PaddingValues,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    val m = HihuTheme.metrics
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item(key = "header") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = m.gapXl, end = m.gapSm),
            ) {
                Text(
                    stringResource(R.string.search_recent),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClear) { Text(stringResource(R.string.search_recent_clear)) }
            }
        }
        items(queries, key = { it }) { query ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(query) }
                    .padding(start = m.gapXl, end = m.gapXs),
            ) {
                Icon(
                    painterResource(Icons.history),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(m.iconMd),
                )
                Text(
                    query,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = m.gapLg),
                )
                IconButton(onClick = { onRemove(query) }) {
                    Icon(
                        painterResource(Icons.close),
                        contentDescription = stringResource(R.string.search_recent_remove, query),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(m.iconSm),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    text: String,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val m = HihuTheme.metrics
    TextField(
        value = text,
        onValueChange = onTextChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(painterResource(Icons.search), contentDescription = null, Modifier.size(m.iconMd)) },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(painterResource(Icons.close), stringResource(R.string.clear), Modifier.size(m.iconMd))
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        shape = RoundedCornerShape(m.radiusPill),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}
