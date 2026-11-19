package com.daffa.hihu.router

/** Every place the app can show. Adding a screen starts here. */
sealed interface Destination {
    /** A destination reached from the bottom navigation. Switching tabs resets the history. */
    sealed interface Tab : Destination

    /** Shelves of music to start from, made on the phone and from YouTube Music. */
    data object Home : Tab

    /** Searching YouTube Music. */
    data object Search : Tab

    /** What is trending, from public charts. */
    data object Explore : Tab

    /** The songs stored on the device. */
    data object Library : Tab

    /** An album or artist, opened over the current tab. [id] is the provider's reference to it. */
    data class Collection(val id: String) : Destination

    /** A mood or genre's page. [ref] is the provider's reference; [title] is shown at the top. */
    data class Mood(val ref: String, val title: String) : Destination

    /** One of the user's own playlists. */
    data class Playlist(val id: Long) : Destination

    /** The play queue, full screen like the player. */
    data object Queue : Destination

    /** Theme and audio quality. */
    data object Settings : Destination

    /** The provider's sign-in page, full screen. */
    data object SignIn : Destination

    /** The full-screen now-playing view. */
    data object Player : Destination

    companion object {
        /** The bottom navigation, in order. */
        val tabs: List<Tab> = listOf(Home, Search, Explore, Library)
    }
}
