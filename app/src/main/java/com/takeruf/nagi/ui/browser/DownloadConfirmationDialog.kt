package com.takeruf.nagi.ui.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.takeruf.nagi.R
import com.takeruf.nagi.browser.engine.DownloadConfirmation
import com.takeruf.nagi.ui.localization.rememberNagiStrings
import com.takeruf.nagi.ui.theme.NagiShapes
import com.takeruf.nagi.ui.theme.NagiSystemBars

@Composable
fun DownloadConfirmationDialog(request: DownloadConfirmation, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val strings = rememberNagiStrings()
    val windowInfo = LocalWindowInfo.current
    val maxHeight = with(LocalDensity.current) { windowInfo.containerSize.height.toDp() } - 48.dp
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        NagiSystemBars()
        BrowserNoticeCard(strings(R.string.ui_download_file), strings(R.string.ui_cancel), onDismiss,
            modifier = Modifier.padding(24.dp).widthIn(max = 360.dp).fillMaxWidth()
                .heightIn(max = maxHeight.coerceAtLeast(160.dp))
                .testTag("download-confirmation")) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(request.name, style = MaterialTheme.typography.bodyLarge)
                request.size?.let { Text("${it / 1024} KB", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(request.source, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, shape = NagiShapes.Rounded,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("download-cancel"),
                    contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(strings(R.string.ui_cancel))
                }
                Button(onClick = onConfirm, shape = NagiShapes.Rounded,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("download-confirm"),
                    contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(strings(R.string.ui_download))
                }
            }
        }
    }
}
