package com.daffa.hihu.views.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.daffa.hihu.ui.HihuTheme

/** A centred line of text for an empty or failed screen, with an optional button under it. */
@Composable
fun Message(text: String, action: String? = null, onAction: () -> Unit = {}) {
    val m = HihuTheme.metrics
    Column(
        modifier = Modifier.fillMaxSize().padding(m.gapXl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (action != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = m.gapLg)) { Text(action) }
        }
    }
}
