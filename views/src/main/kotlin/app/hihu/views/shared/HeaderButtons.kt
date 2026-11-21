package app.hihu.views.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import app.hihu.ui.HihuTheme

/**
 * The buttons under a collection's title, centred. On a narrow screen or with large text they
 * wrap onto another line instead of squeezing their labels.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HeaderButtons(modifier: Modifier = Modifier, content: @Composable FlowRowScope.() -> Unit) {
    val m = HihuTheme.metrics
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(m.gapMd, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(m.gapSm),
        modifier = modifier.fillMaxWidth(),
        content = content,
    )
}

/** A button's label after its icon, kept on one line. */
@Composable
fun ButtonLabel(text: String) {
    val m = HihuTheme.metrics
    Text(text, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = m.gapSm))
}
