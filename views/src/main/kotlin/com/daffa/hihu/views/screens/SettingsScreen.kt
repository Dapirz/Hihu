package com.daffa.hihu.views.screens

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
import com.daffa.hihu.music.AudioQuality
import com.daffa.hihu.state.Appearance
import com.daffa.hihu.state.Preferences
import com.daffa.hihu.ui.HihuTheme
import com.daffa.hihu.ui.Icons
import com.daffa.hihu.views.R

/** Theme and audio quality, each a group of radio buttons. Changes are saved straight away. */
@Composable
fun SettingsScreen(preferences: Preferences, onBack: () -> Unit) {
    val m = HihuTheme.metrics
    val appearance by preferences.appearance.collectAsStateWithLifecycle()
    val quality by preferences.quality.collectAsStateWithLifecycle()

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
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = m.gapXl)) {
            Section(stringResource(R.string.settings_theme))
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
            Section(stringResource(R.string.settings_quality))
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
            Text(
                stringResource(R.string.quality_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = m.gapXl, vertical = m.gapSm),
            )
        }
    }
}

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
