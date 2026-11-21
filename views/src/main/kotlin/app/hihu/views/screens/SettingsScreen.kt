package app.hihu.views.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hihu.music.AudioQuality
import android.text.format.Formatter
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.clickable
import app.hihu.state.Account
import app.hihu.state.AccountStatus
import app.hihu.views.shared.Artwork
import app.hihu.state.Appearance
import app.hihu.state.Downloads
import app.hihu.state.HiddenSongs
import app.hihu.state.BugReport
import android.content.Intent
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.text.style.TextOverflow
import app.hihu.views.chrome.ConfirmDialog
import app.hihu.state.Preferences
import app.hihu.ui.HihuTheme
import app.hihu.ui.Icons
import app.hihu.views.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Switch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.hihu.state.Palette
import app.hihu.views.shared.availablePalettes
import app.hihu.views.shared.colors
import app.hihu.views.shared.label

/**
 * Theme, colours, audio quality, playback, hidden songs and downloads. Changes are saved straight
 * away.
 */
@Composable
fun SettingsScreen(
    preferences: Preferences,
    downloads: Downloads,
    hidden: HiddenSongs,
    bugReport: BugReport,
    account: Account,
    onSignIn: () -> Unit,
    onBack: () -> Unit,
) {
    val m = HihuTheme.metrics
    val appearance by preferences.appearance.collectAsStateWithLifecycle()
    val quality by preferences.quality.collectAsStateWithLifecycle()
    val qualityMobile by preferences.qualityMobile.collectAsStateWithLifecycle()
    val resumeOnConnect by preferences.resumeOnConnect.collectAsStateWithLifecycle()
    val qualityDownload by preferences.qualityDownload.collectAsStateWithLifecycle()
    val downloadLikes by preferences.downloadLikes.collectAsStateWithLifecycle()
    val palette by preferences.palette.collectAsStateWithLifecycle()
    val black by preferences.black.collectAsStateWithLifecycle()
    val normalize by preferences.normalize.collectAsStateWithLifecycle()
    val autoplay by preferences.autoplay.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = m.gapXs)) {
            IconButton(onClick = onBack) {
                Icon(painterResource(Icons.back), stringResource(R.string.back), Modifier.size(m.iconMd))
            }
            Text(
                stringResource(R.string.settings),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = m.gapSm),
            )
        }
        // On a wide screen the settings keep a readable width, centred.
        Column(
            Modifier
                .fillMaxWidth()
                .wrapContentWidth()
                .widthIn(max = m.contentMaxWidth)
                .verticalScroll(rememberScrollState())
                .padding(bottom = m.gapXl),
        ) {
            SettingsCard(R.string.section_account) {
                AccountSection(account, onSignIn)
            }
            SettingsCard(R.string.settings_appearance) {
                SubHeading(R.string.settings_theme)
                Choices(
                    options = Appearance.entries,
                    selected = appearance,
                    label = {
                        when (it) {
                            Appearance.System -> R.string.theme_system
                            Appearance.Light -> R.string.theme_light
                            Appearance.Dark -> R.string.theme_dark
                        }
                    },
                    detail = { null },
                    onSelect = preferences::setAppearance,
                )
                CardDivider()
                SubHeading(R.string.settings_colors)
                Swatches(selected = palette, onSelect = preferences::setPalette)
                Toggle(
                    title = R.string.theme_black,
                    detail = R.string.theme_black_detail,
                    checked = black,
                    onChange = preferences::setBlack,
                )
            }
            SettingsCard(R.string.settings_playback) {
                SubHeading(R.string.quality_wifi)
                Choices(
                    options = AudioQuality.entries,
                    selected = quality,
                    label = {
                        when (it) {
                            AudioQuality.High -> R.string.quality_high
                            AudioQuality.Normal -> R.string.quality_normal
                            AudioQuality.DataSaver -> R.string.quality_saver
                        }
                    },
                    detail = {
                        when (it) {
                            AudioQuality.High -> R.string.quality_high_detail
                            AudioQuality.Normal -> R.string.quality_normal_detail
                            AudioQuality.DataSaver -> R.string.quality_saver_detail
                        }
                    },
                    onSelect = preferences::setQuality,
                )
                CardDivider()
                SubHeading(R.string.quality_mobile)
                Choices(
                    options = AudioQuality.entries,
                    selected = qualityMobile,
                    label = {
                        when (it) {
                            AudioQuality.High -> R.string.quality_high
                            AudioQuality.Normal -> R.string.quality_normal
                            AudioQuality.DataSaver -> R.string.quality_saver
                        }
                    },
                    detail = { null },
                    onSelect = preferences::setQualityMobile,
                )
                Text(
                    stringResource(R.string.quality_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = m.gapLg, vertical = m.gapSm),
                )
                CardDivider()
                Toggle(
                    title = R.string.normalize,
                    detail = R.string.normalize_detail,
                    checked = normalize,
                    onChange = preferences::setNormalize,
                )
                Toggle(
                    title = R.string.autoplay,
                    detail = R.string.autoplay_detail,
                    checked = autoplay,
                    onChange = preferences::setAutoplay,
                )
                Toggle(
                    title = R.string.resume_on_connect,
                    detail = R.string.resume_on_connect_detail,
                    checked = resumeOnConnect,
                    onChange = preferences::setResumeOnConnect,
                )
            }
            SettingsCard(R.string.tab_library) {
                HiddenSection(hidden)
                CardDivider()
                DownloadsSection(downloads)
                Toggle(
                    title = R.string.download_likes,
                    detail = R.string.download_likes_detail,
                    checked = downloadLikes,
                    onChange = preferences::setDownloadLikes,
                )
                SubHeading(R.string.quality_download)
                Choices(
                    options = AudioQuality.entries,
                    selected = qualityDownload,
                    label = {
                        when (it) {
                            AudioQuality.High -> R.string.quality_high
                            AudioQuality.Normal -> R.string.quality_normal
                            AudioQuality.DataSaver -> R.string.quality_saver
                        }
                    },
                    detail = { null },
                    onSelect = preferences::setQualityDownload,
                )
            }
            SettingsCard(R.string.settings_about) {
                SendLog(bugReport)
            }
        }
    }
}

/**
 * One group of settings: its title, then its rows on a rounded card, so the groups stand apart
 * on a long screen. Rows inside line up [HihuTheme.metrics]'s gapLg from the card's edge.
 */
@Composable
private fun SettingsCard(title: Int, content: @Composable ColumnScope.() -> Unit) {
    val m = HihuTheme.metrics
    Section(stringResource(title))
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(m.radiusLg),
        modifier = Modifier.fillMaxWidth().padding(horizontal = m.gapLg),
    ) {
        Column(Modifier.padding(vertical = m.gapSm), content = content)
    }
}

/** A thin line between two parts of one card. */
@Composable
private fun CardDivider() {
    val m = HihuTheme.metrics
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = m.gapLg, vertical = m.gapSm),
    )
}

/** The app's version, and a button that shares its log for a bug report. */
@Composable
private fun SendLog(bugReport: BugReport) {
    val m = HihuTheme.metrics
    val context = LocalContext.current
    val title = stringResource(R.string.send_log)
    Text(
        stringResource(R.string.send_log_detail),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = m.gapLg, vertical = m.gapSm),
    )
    TextButton(
        onClick = {
            bugReport.build { text ->
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                context.startActivity(Intent.createChooser(send, title))
            }
        },
        modifier = Modifier.padding(horizontal = m.gapMd),
    ) { Text(title) }
}

/** A smaller heading inside a card, for one part of it. */
@Composable
private fun SubHeading(title: Int) {
    val m = HihuTheme.metrics
    Text(
        stringResource(title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = m.gapLg, end = m.gapLg, top = m.gapSm, bottom = m.gapXs),
    )
}

/** How many songs are hidden, and a list of them where each can be let back in. */
@Composable
private fun HiddenSection(hidden: HiddenSongs) {
    val m = HihuTheme.metrics
    val songs by hidden.songs.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }

    SubHeading(R.string.hidden_songs)
    Text(
        pluralStringResource(R.plurals.hidden_songs_count, songs.size, songs.size),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(horizontal = m.gapLg),
    )
    TextButton(
        onClick = { open = true },
        enabled = songs.isNotEmpty(),
        modifier = Modifier.padding(horizontal = m.gapMd),
    ) { Text(stringResource(R.string.hidden_songs_show)) }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.hidden_songs)) },
            text = {
                when (songs.isEmpty()) {
                    true -> Text(stringResource(R.string.hidden_songs_empty))
                    false -> LazyColumn(Modifier.heightIn(max = m.dialogListMax)) {
                        items(songs, key = { it.id }) { song ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    Text(song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        song.artist ?: stringResource(R.string.unknown_artist),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                TextButton(onClick = { hidden.show(song) }) { Text(stringResource(R.string.unhide_song)) }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.done)) } },
        )
    }
}

/** How much space downloads take, and a button to remove them all after asking. */
@Composable
private fun DownloadsSection(downloads: Downloads) {
    val m = HihuTheme.metrics
    val context = LocalContext.current
    val songs by downloads.songs.collectAsStateWithLifecycle()
    val bytes by downloads.bytes.collectAsStateWithLifecycle()
    var confirming by remember { mutableStateOf(false) }
    LaunchedEffect(songs.size) { downloads.measure() }

    SubHeading(R.string.settings_downloads)
    Text(
        listOf(
            pluralStringResource(R.plurals.library_count, songs.size, songs.size),
            stringResource(R.string.downloads_used, Formatter.formatShortFileSize(context, bytes)),
        ).joinToString(" · "),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(horizontal = m.gapLg),
    )
    Text(
        stringResource(R.string.downloads_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = m.gapLg, vertical = m.gapXs),
    )
    TextButton(
        onClick = { confirming = true },
        enabled = songs.isNotEmpty(),
        modifier = Modifier.padding(horizontal = m.gapMd),
    ) { Text(stringResource(R.string.downloads_clear)) }

    if (confirming) {
        ConfirmDialog(
            message = stringResource(R.string.downloads_clear_confirm),
            confirm = R.string.delete,
            onConfirm = {
                confirming = false
                downloads.removeAll()
            },
            onDismiss = { confirming = false },
        )
    }
}

/** The YouTube Music account: who is signed in and a way out, or a way in. */
@Composable
private fun AccountSection(account: Account, onSignIn: () -> Unit) {
    val m = HihuTheme.metrics
    val status by account.status.collectAsStateWithLifecycle()
    var confirming by remember { mutableStateOf(false) }

    when (val current = status) {
        is AccountStatus.SignedIn -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = m.gapLg, vertical = m.gapSm),
            ) {
                Artwork(
                    current.profile.photoUri,
                    m.rowArtwork,
                    radius = m.rowArtwork / 2,
                )
                Column(Modifier.weight(1f).padding(horizontal = m.gapLg)) {
                    Text(current.profile.name, style = MaterialTheme.typography.bodyLarge)
                    current.profile.handle?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TextButton(onClick = { confirming = true }) { Text(stringResource(R.string.account_sign_out)) }
            }
            Text(
                stringResource(R.string.account_likes_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = m.gapLg, vertical = m.gapXs),
            )
        }
        AccountStatus.Checking -> Note(stringResource(R.string.account_checking))
        AccountStatus.Unreachable -> {
            Note(stringResource(R.string.account_unreachable))
            TextButton(onClick = account::retryCheck, modifier = Modifier.padding(horizontal = m.gapMd)) {
                Text(stringResource(R.string.retry))
            }
        }
        AccountStatus.SignedOut, AccountStatus.Expired -> {
            if (current == AccountStatus.Expired) Note(stringResource(R.string.account_expired))
            Column(
                Modifier.fillMaxWidth().clickable(onClick = onSignIn).padding(horizontal = m.gapLg, vertical = m.gapSm),
            ) {
                Text(stringResource(R.string.account_sign_in), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.account_sign_in_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(R.string.account_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = m.gapLg, vertical = m.gapXs),
            )
        }
    }

    if (confirming) {
        ConfirmDialog(
            message = stringResource(R.string.account_sign_out_confirm),
            confirm = R.string.account_sign_out,
            onConfirm = {
                confirming = false
                account.signOut()
            },
            onDismiss = { confirming = false },
        )
    }
}

@Composable
private fun Note(text: String) {
    val m = HihuTheme.metrics
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = m.gapLg, vertical = m.gapSm),
    )
}

/** A row of colour circles, one per palette, the chosen one ringed and ticked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Swatches(selected: Palette, onSelect: (Palette) -> Unit) {
    val m = HihuTheme.metrics
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(m.gapMd),
        verticalArrangement = Arrangement.spacedBy(m.gapMd),
        modifier = Modifier
            .padding(horizontal = m.gapLg, vertical = m.gapSm)
            .selectableGroup(),
    ) {
        availablePalettes.forEach { palette ->
            val chosen = palette == selected
            val color = palette.colors.swatch()
            val name = stringResource(palette.label)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(m.swatch)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        when (chosen) {
                            true -> Modifier.border(m.swatchRing, MaterialTheme.colorScheme.onSurface, CircleShape)
                            false -> Modifier
                        },
                    )
                    .selectable(selected = chosen, onClick = { onSelect(palette) }, role = Role.RadioButton)
                    .semantics { contentDescription = name },
            ) {
                if (chosen) {
                    Icon(
                        painterResource(Icons.check),
                        contentDescription = null,
                        tint = if (color.luminance() > HALF) Color.Black else Color.White,
                        modifier = Modifier.size(m.iconMd),
                    )
                }
            }
        }
    }
}

/** A setting that is on or off, with a line explaining it. The whole row toggles. */
@Composable
private fun Toggle(title: Int, detail: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    val m = HihuTheme.metrics
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onChange, role = Role.Switch)
            .padding(horizontal = m.gapLg, vertical = m.gapSm),
    ) {
        Column(Modifier.weight(1f).padding(end = m.gapMd)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

private const val HALF = 0.5f

@Composable
private fun Section(title: String) {
    val m = HihuTheme.metrics
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = m.gapXl, end = m.gapXl, top = m.gapXl, bottom = m.gapSm),
    )
}

/** A radio group: one row per option, with an optional second line explaining it. */
@Composable
private fun <T> Choices(
    options: List<T>,
    selected: T,
    label: (T) -> Int,
    detail: (T) -> Int?,
    onSelect: (T) -> Unit,
) {
    val m = HihuTheme.metrics
    Column(Modifier.selectableGroup()) {
        options.forEach { option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = option == selected, onClick = { onSelect(option) }, role = Role.RadioButton)
                    .padding(horizontal = m.gapLg, vertical = m.gapSm),
            ) {
                RadioButton(selected = option == selected, onClick = null)
                Column(Modifier.padding(start = m.gapMd)) {
                    Text(stringResource(label(option)), style = MaterialTheme.typography.bodyLarge)
                    detail(option)?.let {
                        Text(
                            stringResource(it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
