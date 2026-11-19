package com.daffa.hihu.views.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daffa.hihu.music.Collection
import com.daffa.hihu.state.Account
import com.daffa.hihu.state.AccountStatus
import com.daffa.hihu.state.LibraryKind
import com.daffa.hihu.ui.HihuTheme
import com.daffa.hihu.views.R
import com.daffa.hihu.views.shared.CollectionList
import com.daffa.hihu.views.shared.Message
import com.daffa.hihu.views.shared.more

/**
 * The library of the signed-in YouTube Music account: its playlists, albums or artists, one
 * chip each. Signed out, it offers to sign in instead.
 */
@Composable
fun AccountLibrary(account: Account, padding: PaddingValues, onOpen: (Collection) -> Unit, onSignIn: () -> Unit) {
    val status by account.status.collectAsStateWithLifecycle()
    when (status) {
        AccountStatus.SignedOut -> Message(stringResource(R.string.account_signed_out), stringResource(R.string.account_sign_in), onSignIn)
        AccountStatus.Expired -> Message(stringResource(R.string.account_expired), stringResource(R.string.account_sign_in), onSignIn)
        AccountStatus.Unreachable -> Message(stringResource(R.string.account_unreachable), stringResource(R.string.retry), account::retryCheck)
        AccountStatus.Checking -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        is AccountStatus.SignedIn -> SavedCollections(account, padding, onOpen)
    }
}

@Composable
private fun SavedCollections(account: Account, padding: PaddingValues, onOpen: (Collection) -> Unit) {
    val m = HihuTheme.metrics
    var kind by rememberSaveable { mutableStateOf(LibraryKind.Playlists) }
    val paged by account.library(kind).collectAsStateWithLifecycle()

    Column {
        Row(
            horizontalArrangement = Arrangement.spacedBy(m.gapSm),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = m.gapLg, vertical = m.gapMd),
        ) {
            LibraryKind.entries.forEach { option ->
                val label = when (option) {
                    LibraryKind.Playlists -> R.string.section_playlists
                    LibraryKind.Albums -> R.string.library_albums
                    LibraryKind.Artists -> R.string.library_artists
                }
                FilterChip(
                    selected = option == kind,
                    onClick = { kind = option },
                    label = { Text(stringResource(label)) },
                    shape = RoundedCornerShape(m.radiusPill),
                )
            }
        }
        when {
            paged.loading || !paged.loaded -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            paged.failed -> Message(stringResource(R.string.account_library_failed), stringResource(R.string.retry)) { account.retry(kind) }
            paged.items.isEmpty() -> Message(stringResource(R.string.account_library_empty))
            else -> CollectionList(paged.items, padding, onOpen, more = paged.more { account.loadMore(kind) })
        }
    }
}
