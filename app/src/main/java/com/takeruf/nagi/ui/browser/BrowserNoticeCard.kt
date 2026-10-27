package com.takeruf.nagi.ui.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.takeruf.nagi.ui.components.NagiIcons
import com.takeruf.nagi.ui.theme.NagiShapes

/** Browser notices share their top-right position, corners, header and elevation. */
fun BoxScope.browserNoticePlacement(availableWidth: Dp): Modifier =
    Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 16.dp)
        .widthIn(max = (availableWidth - 32.dp).coerceIn(200.dp, 360.dp))

@Composable
fun BrowserNoticeCard(title: String, dismissLabel: String, onDismiss: () -> Unit,
    modifier: Modifier = Modifier, header: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(NagiShapes.Radius + 8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shadowElevation = 6.dp) {
        Column(Modifier.padding(bottom = 8.dp)) {
            if (header != null) header() else Row(Modifier.padding(start = 16.dp, end = 8.dp)) {
                Text(title, Modifier.weight(1f).padding(top = 16.dp), style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = onDismiss) { Icon(NagiIcons.X, dismissLabel, Modifier.size(18.dp)) }
            }
            content()
        }
    }
}
