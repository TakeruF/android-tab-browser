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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.takeruf.nagi.R
import com.takeruf.nagi.browser.engine.SitePermission
import com.takeruf.nagi.browser.engine.SitePermissionPrompt
import com.takeruf.nagi.ui.localization.rememberNagiStrings
import com.takeruf.nagi.ui.theme.NagiShapes
import com.takeruf.nagi.ui.theme.NagiSystemBars

/** Shared notice-card styling, while Android's runtime permission UI remains system-owned. */
@Composable
fun SitePermissionDialog(prompt: SitePermissionPrompt, onAllow: () -> Unit, onBlock: () -> Unit) {
    val strings = rememberNagiStrings()
    val labels = prompt.permissions.joinToString(", ") { strings(when (it) {
        SitePermission.CAMERA -> R.string.ui_camera
        SitePermission.MICROPHONE -> R.string.ui_microphone
        SitePermission.LOCATION -> R.string.ui_location
    }) }
    val maxHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } - 48.dp
    Dialog(onDismissRequest = onBlock, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        NagiSystemBars()
        BrowserNoticeCard(strings(R.string.ui_allow_site_access), strings(R.string.ui_block), onBlock,
            modifier = Modifier.padding(24.dp).widthIn(max = 360.dp).fillMaxWidth()
                .heightIn(max = maxHeight.coerceAtLeast(160.dp)).testTag("site-permission-dialog"),
            header = { Text(strings(R.string.ui_allow_site_access),
                Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp), style = MaterialTheme.typography.titleSmall) }) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text(strings(R.string.ui_1_s_wants_to_use_your_2_s_access_lasts_for_this_request, prompt.origin, labels),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBlock, shape = NagiShapes.Rounded,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("site-permission-block"),
                    contentPadding = PaddingValues(horizontal = 8.dp)) { Text(strings(R.string.ui_block)) }
                Button(onClick = onAllow, shape = NagiShapes.Rounded,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("site-permission-allow"),
                    contentPadding = PaddingValues(horizontal = 8.dp)) { Text(strings(R.string.ui_allow)) }
            }
        }
    }
}
