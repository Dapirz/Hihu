package app.hihu.views.chrome

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import app.hihu.state.PlaylistSummary
import app.hihu.ui.HihuTheme
import app.hihu.ui.Icons
import app.hihu.views.R
import app.hihu.views.shared.Artwork

/** Picks a playlist to add songs to, or starts a new one. */
@Composable
fun PlaylistPicker(
    playlists: List<PlaylistSummary>,
    onPick: (Long) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    val m = HihuTheme.metrics
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_to_playlist)) },
        text = {
            LazyColumn(Modifier.heightIn(max = m.collectionArtwork * 2)) {
                item(key = "new") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onNew).padding(vertical = m.gapSm),
                    ) {
                        Icon(painterResource(Icons.add), contentDescription = null, Modifier.size(m.iconLg))
                        Text(stringResource(R.string.playlist_new), Modifier.padding(start = m.gapMd))
                    }
                }
                items(playlists, key = { it.id }) { playlist ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(playlist.id) }.padding(vertical = m.gapSm),
                    ) {
                        Artwork(playlist.artworkUri, m.iconXl, radius = m.radiusSm)
                        Column(Modifier.padding(start = m.gapMd)) {
                            Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                pluralStringResource(R.plurals.library_count, playlist.count, playlist.count),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Asks for a playlist name, for a new playlist or a rename. */
@Composable
fun NameDialog(title: Int, initial: String, confirm: Int, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.playlist_name)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(stringResource(confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Asks to confirm something that cannot be undone. */
@Composable
fun ConfirmDialog(message: String, confirm: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
