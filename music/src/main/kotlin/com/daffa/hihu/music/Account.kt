package com.daffa.hihu.music

/** Who is signed in, as the provider shows it. */
data class AccountProfile(val name: String, val handle: String?, val photoUri: String?)

/** Thrown when the provider no longer accepts the session, so the user has to sign in again. */
class SessionExpired(message: String) : Exception(message)

/**
 * A provider account, reached with the session cookies of a sign-in the user made on the
 * provider's own page. The cookies stay on the device; callers keep them and hand them in with
 * [useSession] before anything else.
 */
interface MusicAccount {
    /** The provider's sign-in page, opened in a web view. */
    val signInUrl: String

    /** The address whose cookies hold the session; sign-in has finished once a page there loads. */
    val sessionUrl: String

    /** Whether [cookies], read from the web view, belong to a finished sign-in. */
    fun isSignedIn(cookies: String): Boolean

    /** Uses [cookies] for every later call, or signs out when null. */
    fun useSession(cookies: String?)

    /** The signed-in account. Throws [SessionExpired] when the session is no longer accepted. */
    suspend fun profile(): AccountProfile

    /** The playlists in the account's library, liked songs included. */
    suspend fun savedPlaylists(): Page<Collection>

    /** The albums saved to the account's library. */
    suspend fun savedAlbums(): Page<Collection>

    /** The artists the account subscribes to. */
    suspend fun followedArtists(): Page<Collection>
}
