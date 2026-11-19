package com.daffa.hihu.music

/** A chart of songs and the country it is for, as an ISO 3166 code like "ID". */
class Chart(val country: String, val songs: Page<Song>)

/** Public charts that need no account, like YouTube Charts. Callers run it off the main thread. */
interface MusicCharts {
    /** The music trending now in the user's country, or a fallback country when it has no chart. */
    suspend fun trending(): Chart
}
