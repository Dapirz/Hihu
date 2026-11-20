package app.hihu.state

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import app.hihu.music.AccountProfile
import app.hihu.music.Collection
import app.hihu.music.MusicAccount
import app.hihu.music.SessionExpired
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where signing in stands. */
sealed interface AccountStatus {
    data object SignedOut : AccountStatus

    /** A session is saved and being checked. */
    data object Checking : AccountStatus

    data class SignedIn(val profile: AccountProfile) : AccountStatus

    /** The provider stopped accepting the session; the user has to sign in again. */
    data object Expired : AccountStatus

    /** A session is saved but the provider could not be reached to check it. */
    data object Unreachable : AccountStatus
}

/** Which part of the account's library a list holds. */
enum class LibraryKind { Playlists, Albums, Artists }

/**
 * The signed-in account and its library. The session comes from the provider's own sign-in page
 * in a web view; it is kept encrypted on the device and sent only to the provider.
 */
class Account(
    private val source: MusicAccount,
    context: Context,
    private val scope: CoroutineScope,
) {
    private val store = SessionStore(context)

    private val _status = MutableStateFlow<AccountStatus>(AccountStatus.SignedOut)
    val status: StateFlow<AccountStatus> = _status.asStateFlow()

    private val pagers = LibraryKind.entries.associateWith { Pager<Collection>(scope, Collection::id, "account") }

    /** The provider's sign-in page, for the web view. */
    val signInUrl: String get() = source.signInUrl

    /** Where the session's cookies live; the web view reads them once a page there has loaded. */
    val sessionUrl: String get() = source.sessionUrl

    private var job: Job? = null

    init {
        job = scope.launch {
            val saved = withContext(Dispatchers.IO) { store.read() } ?: return@launch
            source.useSession(saved)
            check()
        }
    }

    /** The account's playlists, albums or artists, loaded once signed in. */
    fun library(kind: LibraryKind): StateFlow<Paged<Collection>> = pagers.getValue(kind).state

    fun loadMore(kind: LibraryKind) = pagers.getValue(kind).loadMore()

    fun retry(kind: LibraryKind) = pagers.getValue(kind).retry()

    /** Whether [cookies], read from the sign-in page, show that signing in has finished. */
    fun isComplete(cookies: String): Boolean = source.isSignedIn(cookies)

    /**
     * Keeps the session from a finished sign-in and loads the account. The web view's own copy of
     * the cookies is dropped, so the only one left on the device is the encrypted one.
     */
    fun signIn(cookies: String) {
        if (!source.isSignedIn(cookies)) return
        job?.cancel()
        source.useSession(cookies)
        CookieManager.getInstance().run {
            removeAllCookies(null)
            flush()
        }
        job = scope.launch {
            // Not kept, the session still works until the app closes; signing in again fixes it.
            runCatching { withContext(Dispatchers.IO) { store.write(cookies) } }
                .onFailure { Log.w(TAG, "account: cannot save the session", it) }
            check()
        }
    }

    /** Forgets the session here and in the web view, so the next sign-in starts fresh. */
    fun signOut() {
        job?.cancel()
        source.useSession(null)
        store.clear()
        CookieManager.getInstance().removeAllCookies(null)
        pagers.values.forEach { it.clear() }
        _status.value = AccountStatus.SignedOut
    }

    /** Checks the session again, after it could not be reached. */
    fun retryCheck() {
        if (_status.value != AccountStatus.Unreachable) return
        job?.cancel()
        job = scope.launch { check() }
    }

    private suspend fun check() {
        _status.value = AccountStatus.Checking
        try {
            _status.value = AccountStatus.SignedIn(source.profile())
            pagers.getValue(LibraryKind.Playlists).load { source.savedPlaylists() }
            pagers.getValue(LibraryKind.Albums).load { source.savedAlbums() }
            pagers.getValue(LibraryKind.Artists).load { source.followedArtists() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SessionExpired) {
            Log.w(TAG, "account: the session is no longer accepted")
            source.useSession(null)
            store.clear()
            _status.value = AccountStatus.Expired
        } catch (e: Exception) {
            Log.w(TAG, "account: cannot check the session", e)
            _status.value = AccountStatus.Unreachable
        }
    }

    private companion object {
        const val TAG = "Hihu"
    }
}
