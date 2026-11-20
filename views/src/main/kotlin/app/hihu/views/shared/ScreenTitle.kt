package app.hihu.views.shared

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import app.hihu.ui.HihuTheme
import app.hihu.ui.Icons
import app.hihu.views.R

/**
 * The large accent-coloured title of a tab, with the settings button on the right and, when
 * [onSearch] is given, a search button before it.
 */
@Composable
fun ScreenTitle(text: String, onSettings: () -> Unit, modifier: Modifier = Modifier, onSearch: (() -> Unit)? = null) {
    val m = HihuTheme.metrics
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(start = m.gapXl, end = m.gapSm),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (onSearch != null) {
            IconButton(onClick = onSearch) {
                Icon(painterResource(Icons.search), stringResource(R.string.library_search), Modifier.size(m.iconMd))
            }
        }
        IconButton(onClick = onSettings) {
            Icon(painterResource(Icons.settings), stringResource(R.string.settings), Modifier.size(m.iconMd))
        }
    }
}
