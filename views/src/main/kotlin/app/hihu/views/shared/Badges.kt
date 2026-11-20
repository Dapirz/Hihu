package app.hihu.views.shared

import androidx.compose.runtime.staticCompositionLocalOf

/** A mark at the end of a song row: downloaded, or how far its download has got. */
sealed interface RowBadge {
    data object Downloaded : RowBadge

    /** Downloading; [percent] runs from 0 to 100. */
    data class Downloading(val percent: Int) : RowBadge

    /** The download stopped with an error; the song's menu offers to try again. */
    data object Failed : RowBadge
}

/**
 * The badge of every song that has one, by song id. The root shell provides it from the
 * downloads, so rows anywhere show it without each screen passing it down.
 */
val LocalSongBadges = staticCompositionLocalOf<Map<String, RowBadge>> { emptyMap() }
