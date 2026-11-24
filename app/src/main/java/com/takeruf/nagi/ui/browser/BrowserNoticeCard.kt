package com.takeruf.nagi.ui.browser

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.takeruf.nagi.ui.components.NagiIcons
import com.takeruf.nagi.ui.components.LocalNagiUiDimensions
import com.takeruf.nagi.ui.theme.NagiShapes

/** Follow the active address bar while keeping notices clear of toolbar and keyboard. */
@Composable
fun BoxScope.browserNoticePlacement(availableWidth: Dp, bottomBar: Boolean = false): Modifier {
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val offset = LocalNagiUiDimensions.current.toolbarHeight + 16.dp
    return Modifier.align(if (bottomBar) Alignment.BottomEnd else Alignment.TopEnd)
        .then(if (bottomBar) Modifier.imePadding() else Modifier)
        .padding(top = if (bottomBar) 0.dp else offset, end = 16.dp,
            bottom = if (bottomBar) (if (imeVisible) 16.dp else offset) else 0.dp)
        .widthIn(max = (availableWidth - 32.dp).coerceIn(200.dp, 360.dp))
}

@Composable
fun BrowserNoticeCard(title: String, dismissLabel: String, onDismiss: () -> Unit,
    modifier: Modifier = Modifier, header: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(NagiShapes.Radius + 8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp) {
        Column(Modifier.padding(bottom = 8.dp)) {
            if (header != null) header() else Row(Modifier.padding(start = 16.dp, end = 8.dp)) {
                Text(title, Modifier.weight(1f).padding(top = 16.dp), style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = onDismiss) { Icon(NagiIcons.X, dismissLabel, Modifier.size(18.dp)) }
            }
            content()
        }
    }
}
