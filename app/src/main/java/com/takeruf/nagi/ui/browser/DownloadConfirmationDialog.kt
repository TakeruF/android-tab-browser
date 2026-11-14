package com.takeruf.nagi.ui.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.takeruf.nagi.R
import com.takeruf.nagi.browser.engine.DownloadConfirmation
import com.takeruf.nagi.ui.localization.rememberNagiStrings
import com.takeruf.nagi.ui.theme.NagiShapes
import com.takeruf.nagi.ui.theme.NagiSystemBars

@Composable
fun DownloadConfirmationDialog(request: DownloadConfirmation, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val strings = rememberNagiStrings()
    AlertDialog(onDismissRequest = onDismiss, modifier = Modifier.testTag("download-confirmation"),
        title = { NagiSystemBars(); Text(strings(R.string.ui_download_file)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(request.name, style = MaterialTheme.typography.bodyLarge)
                request.size?.let { Text("${it / 1024} KB") }
                Text(request.source, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, shape = NagiShapes.Rounded, modifier = Modifier.testTag("download-confirm")) {
                Text(strings(R.string.ui_download))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = NagiShapes.Rounded, modifier = Modifier.testTag("download-cancel")) {
                Text(strings(R.string.ui_cancel))
            }
        })
}
