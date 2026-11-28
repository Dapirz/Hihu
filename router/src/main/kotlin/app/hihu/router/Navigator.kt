package app.hihu.router

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The navigation history as a stack of [Destination]s. The bottom entry is always a tab and is
 * never popped, so the system back gesture leaves the app from there.
 */
class Navigator(root: Destination.Tab) {
    private val _stack = MutableStateFlow<List<Destination>>(listOf(root))

    /** The full history, root first. The last entry is what is on screen. */
    val stack: StateFlow<List<Destination>> = _stack.asStateFlow()

    /** The tab at the bottom of the stack, which the bottom navigation highlights. */
    val tab: Destination.Tab get() = _stack.value.first() as Destination.Tab

    /** Opens [destination] on top, unless it is already showing. */
    fun push(destination: Destination) {
        // Going back to a page already open drops what was opened over it, so A → B → A is one A.
        _stack.update { stack ->
            when (val at = stack.indexOf(destination)) {
                -1 -> stack + destination
                else -> stack.take(at + 1)
            }
        }
    }

    /** Switches to [tab], dropping whatever was open on top of the current one. */
    fun select(tab: Destination.Tab) {
        _stack.value = listOf(tab)
    }

    /** Goes back one step. Returns false when already at the root. */
    fun pop(): Boolean {
        if (_stack.value.size <= 1) return false
        _stack.update { it.dropLast(1) }
        return true
    }
}
