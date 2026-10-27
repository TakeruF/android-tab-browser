package com.takeruf.nagi.ui.browser

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.takeruf.nagi.ui.components.NagiIcons
import com.takeruf.nagi.ui.theme.NagiShapes

/** Shared page-level notice styling, below the address bar and above the web content. */
@Composable
fun BrowserStatusBar(message: String, actionLabel: String, onAction: () -> Unit,
    modifier: Modifier = Modifier, messageModifier: Modifier = Modifier, actionModifier: Modifier = Modifier,
    busy: Boolean = false, maxLines: Int = 1,
    attribution: (@Composable () -> Unit)? = null,
    onDismiss: (() -> Unit)? = null, dismissLabel: String? = null, dismissModifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(message, Modifier.weight(1f).then(messageModifier), style = MaterialTheme.typography.bodySmall,
                maxLines = maxLines, overflow = TextOverflow.Ellipsis)
            if (attribution != null) {
                Spacer(Modifier.width(8.dp))
                attribution()
            }
            TextButton(onClick = onAction, modifier = actionModifier, shape = NagiShapes.Rounded,
                contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(actionLabel, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            if (onDismiss != null) IconButton(onClick = onDismiss, modifier = dismissModifier.size(48.dp)) {
                Icon(NagiIcons.X, dismissLabel, Modifier.size(18.dp))
            }
        }
    }
}
