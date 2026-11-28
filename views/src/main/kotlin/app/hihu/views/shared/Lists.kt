package app.hihu.views.shared

import android.icu.text.CompactDecimalFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.background
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import app.hihu.music.Collection
import app.hihu.music.CollectionKind
import app.hihu.music.Song
import app.hihu.state.Paged
import app.hihu.ui.HihuTheme
import app.hihu.ui.Icons
import app.hihu.views.R
import kotlinx.coroutines.flow.filter
import java.util.Locale

/** Whether a list can grow when it is scrolled to the end, and how that is going. */
data class ListMore(
    val available: Boolean,
    val loading: Boolean,
    val failed: Boolean,
    val onLoad: () -> Unit,
)

/**
 * The items of a song's ⋮ menu. It gets the song, its index in the list and a function that
 * closes the menu, which each item calls after acting.
 */
typealias SongMenu = @Composable ColumnScope.(song: Song, index: Int, dismiss: () -> Unit) -> Unit

/** The [ListMore] for a paged list. */
fun Paged<*>.more(onLoad: () -> Unit) = ListMore(hasMore, loadingMore, moreFailed, onLoad)

/**
 * A list of songs with dividers between rows. The row of [playingId] is shown in the accent
 * colour with an equaliser mark. Tapping a row calls [onPlay] with its index. [header] scrolls
 * with the list, above the first row.
 *
 * Given a [selectionBar], holding a row starts choosing several songs: taps then add or take
 * away rows, the bar shows above the list with the songs chosen, and back ends it.
 */
@Composable
fun SongList(
    songs: List<Song>,
    playingId: String?,
    padding: PaddingValues,
    onPlay: (Int) -> Unit,
    modifier: Modifier = Modifier,
    more: ListMore? = null,
    header: (@Composable () -> Unit)? = null,
    menu: SongMenu? = null,
    // A list where a song can appear twice, like the queue, keys and marks rows by position.
    key: (Int, Song) -> Any = { _, song -> song.id },
    isPlaying: (Int, Song) -> Boolean = { _, song -> song.id == playingId },
    /** Given, every row gets a drag handle, and a drop calls this once with both positions. */
    onMove: ((from: Int, to: Int) -> Unit)? = null,
    /** Shows each row's position, 1 first, before its cover, like a chart. */
    numbered: Boolean = false,
    selectionBar: (@Composable (chosen: List<Song>, done: () -> Unit) -> Unit)? = null,
    dividers: Boolean = true,
    /** Rows drawn faded, like the songs of the queue that already played. */
    dimmed: (Int, Song) -> Boolean = { _, _ -> false },
    /** The row the list opens at, rather than the first. */
    initialIndex: Int? = null,
    /** Given, a row can be swiped away sideways when [canRemove] allows it; this gets its index. */
    onRemove: ((Int) -> Unit)? = null,
    canRemove: (Int, Song) -> Boolean = { _, _ -> true },
) {
    val m = HihuTheme.metrics
    val list = rememberLazyListState(
        initialFirstVisibleItemIndex = (initialIndex ?: 0).coerceAtLeast(0) + if (header != null) 1 else 0,
    )
    var chosen by rememberSaveable { mutableStateOf(listOf<String>()) }
    val choosing = selectionBar != null && chosen.isNotEmpty()
    val toggle = { song: Song ->
        chosen = when (song.id in chosen) {
            true -> chosen - song.id
            false -> chosen + song.id
        }
    }
    BackHandler(enabled = choosing) { chosen = emptyList() }
    // Rows are copied so a drag can reorder them on screen before anything is saved.
    val rows = remember(songs) { songs.mapIndexed { i, song -> ReorderRow(key(i, song), song) }.toMutableStateList() }
    val onMoveNow by rememberUpdatedState(onMove)
    val reorder = remember(list, rows) { ReorderState(list, rows) { from, to -> onMoveNow?.invoke(from, to) } }
    if (onMove != null) AutoScrollWhileDragging(reorder)
    LoadMoreNearEnd(list, more)
    Column(modifier.fillMaxSize()) {
        if (choosing) selectionBar?.invoke(songs.filter { it.id in chosen }) { chosen = emptyList() }
        LazyColumn(Modifier.weight(1f), state = list, contentPadding = padding) {
            if (header != null) item(key = HEADER_KEY) { header() }
            itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                val song = row.item
                val rowMenu: (@Composable ColumnScope.(() -> Unit) -> Unit)? =
                    menu?.let { items -> { dismiss -> items(song, index, dismiss) } }
                val dragged = reorder.dragging == row.key
                val handle: (@Composable () -> Unit)? = onMove?.let {
                    {
                        Icon(
                            painterResource(Icons.dragHandle),
                            stringResource(R.string.drag_to_reorder),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            // The whole 48 dp square starts a drag; the padding only shrinks the drawn icon.
                            modifier = Modifier.size(m.roundButton).dragHandle(reorder, row.key).padding(m.gapMd),
                        )
                    }
                }
                Column(
                    Modifier
                        .then(if (dragged) Modifier else Modifier.animateItem())
                        .zIndex(if (dragged) 1f else 0f)
                        .graphicsLayer { translationY = if (dragged) reorder.offset else 0f }
                        .background(if (dragged) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent),
                ) {
                    val songRow: @Composable () -> Unit = { SongRow(
                        song,
                        playing = isPlaying(index, song),
                        onClick = {
                            when (choosing) {
                                true -> toggle(song)
                                false -> onPlay(index)
                            }
                        },
                        menu = rowMenu,
                        handle = handle,
                        rank = if (numbered) index + 1 else null,
                        selected = choosing && song.id in chosen,
                        onLongClick = selectionBar?.let { { toggle(song) } },
                    ) }
                    Box(Modifier.alpha(if (dimmed(index, song) && !dragged) DIMMED_ALPHA else 1f)) {
                        // The row stays in the same wrapper while it is dragged: swapping it out would
                        // drop the drag handle mid-drag. Only the swipe is switched off.
                        when (onRemove != null) {
                            true -> SwipeToRemove(
                                enabled = !dragged && canRemove(index, song),
                                onRemove = { onRemove(index) },
                                content = songRow,
                            )
                            false -> songRow()
                        }
                    }
                    if (dividers && index < rows.lastIndex && !dragged) RowDivider(m.gapLg + m.rowArtwork + m.gapLg)
                }
            }
            moreFooter(more)
        }
    }
}

/** [content] on a surface that a sideways swipe takes away, over a red strip with a bin. */
@Composable
private fun SwipeToRemove(enabled: Boolean, onRemove: () -> Unit, content: @Composable () -> Unit) {
    val m = HihuTheme.metrics
    val state = rememberSwipeToDismissBoxState()
    val settle = rememberCoroutineScope()
    val label = stringResource(R.string.remove_from_queue)
    SwipeToDismissBox(
        state = state,
        gesturesEnabled = enabled,
        modifier = Modifier.semantics {
            if (enabled) customActions = listOf(CustomAccessibilityAction(label) { onRemove(); true })
        },
        backgroundContent = {
            val alignment = when (state.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                else -> Alignment.CenterEnd
            }
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = m.gapXl),
                contentAlignment = alignment,
            ) {
                Icon(painterResource(Icons.delete), null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(m.iconMd))
            }
        },
        onDismiss = {
            onRemove()
            // Settled again at once: a row that comes back under the same key, as an undo brings
            // it, must not arrive swiped away and be taken out a second time.
            settle.launch { state.snapTo(SwipeToDismissBoxValue.Settled) }
        },
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.surface)) { content() }
    }
}

/** A list of albums or artists. Tapping one calls [onOpen]. */
@Composable
fun CollectionList(
    collections: List<Collection>,
    padding: PaddingValues,
    onOpen: (Collection) -> Unit,
    modifier: Modifier = Modifier,
    more: ListMore? = null,
) {
    val m = HihuTheme.metrics
    val list = rememberLazyListState()
    LoadMoreNearEnd(list, more)
    LazyColumn(modifier.fillMaxSize(), state = list, contentPadding = padding) {
        itemsIndexed(collections, key = { _, c -> c.id }) { index, collection ->
            CollectionRow(collection) { onOpen(collection) }
            if (index < collections.lastIndex) RowDivider(m.gapLg + m.rowArtwork + m.gapLg)
        }
        moreFooter(more)
    }
}

/**
 * One song: cover, title, and artist with the length, and a ⋮ button when it has a [menu]. A
 * [selected] row is tinted; [onLongClick], when given, is what holding the row does.
 */
@Composable
fun SongRow(
    song: Song,
    playing: Boolean,
    onClick: () -> Unit,
    menu: (@Composable ColumnScope.(dismiss: () -> Unit) -> Unit)? = null,
    handle: (@Composable () -> Unit)? = null,
    rank: Int? = null,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val m = HihuTheme.metrics
    val (titleColor, subColor) = when (playing) {
        true -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primary
        false -> MaterialTheme.colorScheme.onSurface to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val playingLabel = stringResource(R.string.playing_now)
    val artist = song.artist ?: stringResource(R.string.unknown_artist)
    val subtitle = when (song.durationMs > 0) {
        true -> "$artist · ${formatDuration(song.durationMs)}"
        false -> artist
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = onLongClick?.let { stringResource(R.string.select_songs) })
            .semantics {
                if (playing) stateDescription = playingLabel
                if (onLongClick != null) this.selected = selected
            }
            .padding(start = m.gapLg, end = if (menu != null || handle != null) m.gapXs else m.gapLg, top = m.gapMd, bottom = m.gapMd),
    ) {
        if (rank != null) {
            Text(
                rank.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(m.rankWidth),
            )
        }
        Artwork(song.artworkUri, m.rowArtwork, radius = m.radiusMd)
        Column(Modifier.weight(1f).padding(start = m.gapLg)) {
            Text(song.title, color = titleColor, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = subColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when (val badge = LocalSongBadges.current[song.id]) {
            RowBadge.Downloaded -> Icon(
                painterResource(Icons.downloadDone),
                contentDescription = stringResource(R.string.downloaded),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = m.gapSm).size(m.iconSm),
            )
            is RowBadge.Downloading -> Text(
                stringResource(R.string.download_percent, badge.percent),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = m.gapSm),
            )
            RowBadge.Failed -> Text(
                stringResource(R.string.download_failed),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = m.gapSm),
            )
            null -> Unit
        }
        if (playing) {
            Icon(
                painterResource(Icons.equalizer),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = m.gapSm).size(m.iconMd),
            )
        }
        if (menu != null) {
            var open by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { open = true }) {
                    Icon(
                        painterResource(Icons.more),
                        stringResource(R.string.more),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(m.iconMd),
                    )
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    menu { open = false }
                }
            }
        }
        handle?.invoke()
    }
}

/** One album (square cover) or artist (round photo), with its subtitle line. */
@Composable
fun CollectionRow(collection: Collection, onClick: () -> Unit) {
    val m = HihuTheme.metrics
    val radius = when (collection.kind) {
        CollectionKind.Album, CollectionKind.Playlist -> m.radiusMd
        CollectionKind.Artist -> m.rowArtwork / 2
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = m.gapLg, vertical = m.gapMd),
    ) {
        Artwork(collection.artworkUri, m.rowArtwork, radius = radius)
        Column(Modifier.weight(1f).padding(start = m.gapLg)) {
            Text(collection.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            collectionSubtitle(collection)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** "Artist · 12 songs" for an album, "1.2M subscribers" for an artist. */
@Composable
fun collectionSubtitle(collection: Collection): String? {
    val count = collection.count
    return when (collection.kind) {
        CollectionKind.Album, CollectionKind.Playlist -> {
            val songs = count?.let { pluralStringResource(R.plurals.library_count, it.toInt(), it.toInt()) }
            listOfNotNull(collection.subtitle, songs).joinToString(" · ").ifEmpty { null }
        }
        CollectionKind.Artist -> count?.let {
            pluralStringResource(R.plurals.subscribers, it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), compact(it))
        }
    }
}

@Composable
private fun RowDivider(start: androidx.compose.ui.unit.Dp) {
    val m = HihuTheme.metrics
    HorizontalDivider(
        thickness = m.divider,
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(start = start, end = m.gapLg),
    )
}

/** Asks for the next page a few rows before the end, while one can be asked for. */
@Composable
internal fun LoadMoreNearEnd(list: LazyListState, more: ListMore?) {
    if (more == null) return
    val onLoad by rememberUpdatedState(more.onLoad)
    val canLoad = more.available && !more.loading && !more.failed
    LaunchedEffect(list, canLoad) {
        if (!canLoad) return@LaunchedEffect
        snapshotFlow {
            val last = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= list.layoutInfo.totalItemsCount - LOAD_AHEAD
        }.filter { it }.collect { onLoad() }
    }
}

/** A spinner under the list while the next page loads, or a retry button when it failed. */
internal fun LazyListScope.moreFooter(more: ListMore?) {
    if (more == null || !(more.loading || more.failed)) return
    item(key = FOOTER_KEY) {
        val m = HihuTheme.metrics
        Box(Modifier.fillMaxWidth().padding(m.gapLg), contentAlignment = Alignment.Center) {
            when (more.failed) {
                true -> TextButton(onClick = more.onLoad) { Text(stringResource(R.string.retry)) }
                false -> CircularProgressIndicator(Modifier.size(m.iconLg))
            }
        }
    }
}

/** One formatter per language, not one per row drawn. */
private var compactFormat: Pair<Locale, CompactDecimalFormat>? = null

private fun compact(count: Long): String {
    val locale = Locale.getDefault()
    val format = compactFormat?.takeIf { it.first == locale }?.second
        ?: CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT).also { compactFormat = locale to it }
    return format.format(count)
}

/** How many rows before the end the next page is asked for. */
private const val LOAD_AHEAD = 6
private const val DIMMED_ALPHA = 0.5f
private const val FOOTER_KEY = "list-more"
private const val HEADER_KEY = "list-header"
