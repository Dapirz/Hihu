package app.hihu.views.shared

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * What the playing song's cover needs to move between the mini player and the full player as one
 * picture: the shared layout around both, and the side of the change it is drawn in.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
class CoverTransition(val shared: SharedTransitionScope, val side: AnimatedVisibilityScope)

/** Set by the shell around the mini player and the full player; null anywhere else. */
val LocalCoverTransition = compositionLocalOf<CoverTransition?> { null }

/**
 * Marks the playing song's cover, so it grows from the mini player into the full player and back.
 * Outside a [CoverTransition] it changes nothing.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.nowPlayingCover(): Modifier {
    val transition = LocalCoverTransition.current ?: return this
    return with(transition.shared) {
        this@nowPlayingCover.sharedBounds(
            rememberSharedContentState(NOW_PLAYING_COVER),
            transition.side,
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
        )
    }
}

private const val NOW_PLAYING_COVER = "now-playing-cover"
