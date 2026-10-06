package com.takeruf.nagi.ui.sidebar

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.takeruf.nagi.R
import com.takeruf.nagi.ui.components.*
import com.takeruf.nagi.ui.localization.rememberNagiStrings
import com.takeruf.nagi.ui.theme.NagiShapes
import kotlinx.coroutines.delay
import java.net.URI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SidebarAddressBar(url: String, title: String, onOmnibox: () -> Unit) {
    val strings = rememberNagiStrings()
    val context = LocalContext.current
    val hasPage = url.isNotBlank() && url != "about:blank"
    var copied by remember(url) { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }
    val copy = {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(title, url))
        copied = true
    }
    val displayUrl = remember(url) {
        runCatching { URI(url).host }.getOrNull() ?: url.removePrefix("https://").removePrefix("http://").removeSuffix("/")
    }
    Surface(onClick = onOmnibox, shape = NagiShapes.Rounded,
        color = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().testTag("sidebar-address")) {
        Row(Modifier.heightIn(min = 48.dp).padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(if (hasPage) displayUrl else strings(R.string.ui_search_anything),
                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (hasPage && title.isNotBlank() && title != url) Text(title,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (hasPage) {
                TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text(strings(if (copied) R.string.ui_link_copied else R.string.ui_copy_link)) } },
                    state = rememberTooltipState()) {
                    AddressActionButton(onClick = copy, modifier = Modifier.testTag("sidebar-copy-url")) {
                        Icon(if (copied) NagiIcons.Check else NagiIcons.Link,
                            strings(if (copied) R.string.ui_link_copied else R.string.ui_copy_link), Modifier.size(16.dp))
                    }
                }
                AddressActionButton(onClick = {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, url)
                        putExtra(Intent.EXTRA_SUBJECT, title)
                    }, strings(R.string.ui_share_link)))
                }, modifier = Modifier.testTag("sidebar-share-url")) {
                    Icon(NagiIcons.Share, strings(R.string.ui_share_link), Modifier.size(16.dp))
                }
            } else if (!hasPage) {
                Text("Ctrl L", Modifier.padding(end = 8.dp), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
