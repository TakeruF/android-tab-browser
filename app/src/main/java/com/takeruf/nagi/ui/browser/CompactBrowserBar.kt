package com.takeruf.nagi.ui.browser

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.takeruf.nagi.R
import com.takeruf.nagi.ui.components.NagiIcons
import com.takeruf.nagi.ui.localization.rememberNagiStrings
import com.takeruf.nagi.ui.theme.NagiShapes

/** Cover-screen controls stay within thumb reach without reserving a sidebar column. */
@Composable
fun CompactBrowserBar(tabCount: Int, canGoBack: Boolean, canGoForward: Boolean,
    onBack: () -> Unit, onForward: () -> Unit, onNewTab: () -> Unit,
    onSearch: () -> Unit, onTabs: () -> Unit) {
    val strings = rememberNagiStrings()
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().height(56.dp).testTag("compact-browser-bar"),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = canGoBack, modifier = Modifier.weight(1f).fillMaxHeight()) {
                Icon(NagiIcons.ArrowBack, strings(R.string.ui_back), Modifier.size(21.dp))
            }
            IconButton(onClick = onForward, enabled = canGoForward, modifier = Modifier.weight(1f).fillMaxHeight()) {
                Icon(NagiIcons.ArrowForward, strings(R.string.ui_forward), Modifier.size(21.dp))
            }
            IconButton(onClick = onNewTab, modifier = Modifier.weight(1f).fillMaxHeight()) {
                Icon(NagiIcons.Plus, strings(R.string.ui_new_tab), Modifier.size(23.dp))
            }
            IconButton(onClick = onSearch, modifier = Modifier.weight(1f).fillMaxHeight()) {
                Icon(NagiIcons.Search, strings(R.string.ui_search_or_enter_url), Modifier.size(21.dp))
            }
            IconButton(onClick = onTabs, modifier = Modifier.weight(1f).fillMaxHeight()
                .testTag("compact-tabs-button").semantics {
                    contentDescription = strings(R.string.ui_tabs)
                    stateDescription = tabCount.toString()
                }) {
                Box(Modifier.size(28.dp).border(1.5.dp, MaterialTheme.colorScheme.onSurface, NagiShapes.Rounded),
                    contentAlignment = Alignment.Center) {
                    Text(tabCount.toString(), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
