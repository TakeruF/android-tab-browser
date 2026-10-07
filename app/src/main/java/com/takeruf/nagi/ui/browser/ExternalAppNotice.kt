package com.takeruf.nagi.ui.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.takeruf.nagi.R
import com.takeruf.nagi.browser.engine.BlockedExternalAppRequest
import com.takeruf.nagi.ui.localization.rememberNagiStrings
import com.takeruf.nagi.ui.components.NagiIcons
import com.takeruf.nagi.ui.theme.NagiShapes

@Composable
fun ExternalAppNotice(request: BlockedExternalAppRequest, onAllowOnce: () -> Unit,
    onAllowAlways: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val strings = rememberNagiStrings()
    val buttonShape = NagiShapes.Rounded
    val cardShape = RoundedCornerShape(NagiShapes.Radius + 8.dp)
    Surface(modifier.testTag("external-app-notice"), shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shadowElevation = 6.dp) {
        Column(Modifier.padding(bottom = 8.dp)) {
            Row(Modifier.padding(start = 16.dp, end = 8.dp)) {
                Text(strings(R.string.ui_external_apps_blocked), Modifier.weight(1f).padding(top = 16.dp),
                    style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = onDismiss) {
                    Icon(NagiIcons.X, strings(R.string.ui_dismiss_external_app_notice), Modifier.size(18.dp))
                }
            }
            Text(request.url, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp), maxLines = 1,
                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onAllowOnce, shape = buttonShape,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(strings(R.string.ui_allow_once))
                }
                Button(onClick = onAllowAlways, shape = buttonShape,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(strings(R.string.ui_allow_always))
                }
            }
        }
    }
}
