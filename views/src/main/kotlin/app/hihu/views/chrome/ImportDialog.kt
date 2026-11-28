package app.hihu.views.chrome

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hihu.state.ImportFailure
import app.hihu.state.ImportState
import app.hihu.state.PlaylistImport
import app.hihu.ui.HihuTheme
import app.hihu.views.R

/**
 * Copies a YouTube or Spotify playlist into a new Hihu playlist from its link: a field for the
 * link, then progress while every page is read and, for Spotify, while each song is found, then
 * the result with the songs that may be another version or were not found. [initial] fills the
 * field, for a link shared from another app.
 */
@Composable
fun ImportDialog(importer: PlaylistImport, initial: String, onOpen: (Long) -> Unit, onDismiss: () -> Unit) {
    val m = HihuTheme.metrics
    val state by importer.state.collectAsStateWithLifecycle()
    var link by rememberSaveable { mutableStateOf(initial) }
    // A link shared while the dialog is already open fills the field too.
    LaunchedEffect(initial) { if (initial.isNotEmpty()) link = initial }
    val close = {
        importer.reset()
        onDismiss()
    }
    val reading = state is ImportState.Reading || state is ImportState.Matching

    AlertDialog(
        // A tap outside must not throw away a long read halfway.
        onDismissRequest = { if (!reading) close() },
        properties = DialogProperties(dismissOnClickOutside = !reading),
        title = { Text(stringResource(R.string.import_title)) },
        text = {
            when (val current = state) {
                is ImportState.Reading -> Column {
                    Text(
                        when {
                            current.total != null -> stringResource(
                                R.string.import_reading_of,
                                current.fetched,
                                current.total ?: 0L,
                            )
                            else -> pluralStringResource(R.plurals.import_reading, current.fetched, current.fetched)
                        },
                    )
                    current.title?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = m.gapXs),
                        )
                    }
                    val total = current.total
                    when (total != null && total > 0) {
                        true -> LinearProgressIndicator(
                            progress = { (current.fetched.toFloat() / total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(top = m.gapMd),
                        )
                        false -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = m.gapMd))
                    }
                }
                is ImportState.Matching -> Column {
                    Text(stringResource(R.string.import_matching, current.done, current.total))
                    Text(
                        current.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = m.gapXs),
                    )
                    LinearProgressIndicator(
                        progress = { (current.done.toFloat() / current.total.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = m.gapMd),
                    )
                }
                is ImportState.Done -> Column(Modifier.heightIn(max = m.dialogListMax).verticalScroll(rememberScrollState())) {
                    Text(pluralStringResource(R.plurals.import_done, current.count, current.count, current.name))
                    current.cut?.let { (read, all) ->
                        Text(
                            when (all) {
                                null -> stringResource(R.string.import_cut_unknown, read)
                                else -> stringResource(R.string.import_cut, read, all)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = m.gapSm),
                        )
                    }
                    SongNames(pluralStringResource(R.plurals.import_unsure, current.unsure.size, current.unsure.size), current.unsure)
                    SongNames(pluralStringResource(R.plurals.import_missing, current.missing.size, current.missing.size), current.missing)
                }
                else -> Column {
                    Text(
                        stringResource(R.string.import_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = link,
                        onValueChange = { link = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.import_link)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth().padding(top = m.gapMd),
                    )
                    (current as? ImportState.Failed)?.let {
                        Text(
                            stringResource(
                                when (it.reason) {
                                    ImportFailure.NotAPlaylist -> R.string.import_not_playlist
                                    ImportFailure.Unavailable -> R.string.import_unavailable
                                    ImportFailure.Empty -> R.string.import_empty
                                    ImportFailure.Network -> R.string.import_network
                                    ImportFailure.NotASongList -> R.string.import_song_link
                                    ImportFailure.Unreadable -> R.string.import_unreadable
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = m.gapSm),
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (val current = state) {
                is ImportState.Reading, is ImportState.Matching -> Unit
                is ImportState.Done -> TextButton(onClick = {
                    importer.reset()
                    onOpen(current.id)
                }) { Text(stringResource(R.string.import_open)) }
                else -> TextButton(onClick = { importer.start(link) }, enabled = link.isNotBlank()) {
                    Text(stringResource(R.string.import_start))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = close) {
                Text(
                    stringResource(
                        when (state) {
                            is ImportState.Reading, is ImportState.Matching -> R.string.import_stop
                            is ImportState.Done -> R.string.import_close
                            else -> R.string.cancel
                        },
                    ),
                )
            }
        },
    )
}

/** A heading such as "3 not found" that opens to the songs it counts; nothing when there are none. */
@Composable
private fun SongNames(heading: String, names: List<String>) {
    if (names.isEmpty()) return
    val m = HihuTheme.metrics
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = !open }, modifier = Modifier.padding(top = m.gapXs)) { Text(heading) }
    if (open) {
        names.forEach { name ->
            Text(
                name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = m.gapMd, bottom = m.gapXs),
            )
        }
    }
}
