package com.daffa.hihu.views.shared

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.daffa.hihu.ui.HihuTheme
import kotlinx.coroutines.delay

/** One row of a reorderable list: a key that stays with the song, and where it started. */
data class ReorderRow<T>(val key: Any, val item: T, val start: Int)

/**
 * Drag to reorder for a lazy list. Rows swap in [rows] while a row is dragged, so nothing is
 * written until the drop, when [onMove] is told the row's start and end position once. The list
 * scrolls by itself while the row is held near its top or bottom edge.
 */
@Stable
class ReorderState<T>(
    private val list: LazyListState,
    private val rows: SnapshotStateList<ReorderRow<T>>,
    private val onMove: (from: Int, to: Int) -> Unit,
) {
    /** The key of the row being dragged, or null. */
    var dragging by mutableStateOf<Any?>(null)
        private set

    /** How far the dragged row is drawn from its slot, in pixels. */
    var offset by mutableFloatStateOf(0f)
        private set

    private var from = -1

    fun start(key: Any) {
        dragging = key
        offset = 0f
        from = rows.indexOfFirst { it.key == key }
    }

    /** Moves the dragged row by [dy] pixels, swapping it past any neighbour it crosses halfway. */
    fun drag(dy: Float) {
        val key = dragging ?: return
        offset += dy
        val current = info(key) ?: return
        val top = current.offset + offset
        val bottom = top + current.size
        val target = list.layoutInfo.visibleItemsInfo.firstOrNull { other ->
            other.key != key && rows.any { it.key == other.key } && when {
                offset > 0 -> other.index > current.index && bottom > other.offset + other.size / 2
                else -> other.index < current.index && top < other.offset + other.size / 2
            }
        } ?: return
        val i = rows.indexOfFirst { it.key == key }
        val j = rows.indexOfFirst { it.key == target.key }
        if (i < 0 || j < 0) return
        rows.add(j, rows.removeAt(i))
        // The row now sits in the target's slot; keep it under the finger.
        offset -= target.offset - current.offset
    }

    fun end() {
        val key = dragging ?: return
        val to = rows.indexOfFirst { it.key == key }
        dragging = null
        offset = 0f
        if (from >= 0 && to >= 0 && from != to) onMove(from, to)
        from = -1
    }

    /** How far to scroll this frame: towards an edge the dragged row is close to, else zero. */
    internal fun edgeScroll(edge: Float, speed: Float): Float {
        val key = dragging ?: return 0f
        val current = info(key) ?: return 0f
        val top = current.offset + offset
        val bottom = top + current.size
        val start = list.layoutInfo.viewportStartOffset
        val end = list.layoutInfo.viewportEndOffset
        return when {
            top < start + edge -> -speed
            bottom > end - edge -> speed
            else -> 0f
        }
    }

    internal suspend fun scroll(by: Float) {
        val moved = list.scrollBy(by)
        // Scrolling moves the slot under a still finger; the row follows the finger.
        offset += moved
        drag(0f)
    }

    private fun info(key: Any): LazyListItemInfo? = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
}

/** Scrolls the list while the dragged row is held near an edge. */
@Composable
fun AutoScrollWhileDragging(state: ReorderState<*>) {
    val m = HihuTheme.metrics
    val density = LocalDensity.current
    val edge = with(density) { m.reorderEdge.toPx() }
    val speed = with(density) { m.reorderStep.toPx() }
    LaunchedEffect(state.dragging) {
        if (state.dragging == null) return@LaunchedEffect
        while (true) {
            val by = state.edgeScroll(edge, speed)
            if (by != 0f) state.scroll(by)
            delay(FRAME_MS)
        }
    }
}

/** Makes the element a drag handle for the row with [key]. */
fun Modifier.dragHandle(state: ReorderState<*>, key: Any): Modifier = pointerInput(state, key) {
    detectDragGestures(
        onDragStart = { state.start(key) },
        onDrag = { change, amount ->
            change.consume()
            state.drag(amount.y)
        },
        onDragEnd = { state.end() },
        onDragCancel = { state.end() },
    )
}

private const val FRAME_MS = 16L
