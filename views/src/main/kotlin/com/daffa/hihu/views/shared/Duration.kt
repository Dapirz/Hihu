package com.daffa.hihu.views.shared

/** Formats a length in milliseconds as m:ss, or h:mm:ss from an hour up. */
fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return when (h > 0) {
        true -> "%d:%02d:%02d".format(h, m, s)
        false -> "%d:%02d".format(m, s)
    }
}
