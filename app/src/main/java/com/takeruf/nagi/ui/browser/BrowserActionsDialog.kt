package com.takeruf.nagi.ui.browser

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.takeruf.nagi.R
import com.takeruf.nagi.browser.engine.BrowserActionPrompt
import com.takeruf.nagi.ui.localization.rememberNagiStrings
import com.takeruf.nagi.ui.theme.NagiShapes
import com.takeruf.nagi.ui.theme.NagiSystemBars

/** App-owned link/image and upload choices share the browser's confirmation styling. */
@Composable
fun BrowserActionsDialog(prompt: BrowserActionPrompt, onChoose: (Int) -> Unit, onDismiss: () -> Unit) {
    val strings = rememberNagiStrings()
    val maxHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } - 48.dp
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        NagiSystemBars()
        BrowserNoticeCard(prompt.title, strings(R.string.ui_cancel), onDismiss,
            modifier = Modifier.padding(24.dp).widthIn(max = 360.dp).fillMaxWidth()
                .heightIn(max = maxHeight.coerceAtLeast(160.dp)).testTag("browser-actions-dialog"),
            header = { Text(prompt.title, Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall,
                maxLines = 3, overflow = TextOverflow.Ellipsis) }) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
                prompt.actions.forEachIndexed { index, action ->
                    TextButton(onClick = { onChoose(index) }, shape = NagiShapes.Rounded,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("browser-action-$index"),
                        contentPadding = PaddingValues(horizontal = 16.dp)) {
                        Text(action.label, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            OutlinedButton(onClick = onDismiss, shape = NagiShapes.Rounded,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp).fillMaxWidth()
                    .heightIn(min = 48.dp).testTag("browser-actions-cancel")) { Text(strings(R.string.ui_cancel)) }
        }
    }
}
