package com.takeruf.nagi.ui.browser

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.takeruf.nagi.R
import com.takeruf.nagi.ui.components.NagiIcons
import com.takeruf.nagi.ui.components.LocalNagiUiDimensions
import com.takeruf.nagi.ui.localization.rememberNagiStrings

/** Cover-screen controls stay within thumb reach without reserving a sidebar column. */
@Composable
fun CompactBrowserBar(tabCount: Int, canGoBack: Boolean,
    onBack: () -> Unit, onHome: () -> Unit, onNewTab: () -> Unit,
    onSearch: (() -> Unit)?, onTabs: () -> Unit, onPageMenu: () -> Unit, pageMenuTag: String,
    onBarBounds: (Rect) -> Unit = {}) {
    val strings = rememberNagiStrings()
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().height(LocalNagiUiDimensions.current.toolbarHeight).testTag("compact-browser-bar")
            .onGloballyPositioned { onBarBounds(it.boundsInWindow()) },
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onHome, modifier = Modifier.weight(1f).fillMaxHeight().testTag("compact-home-button")) {
                Icon(NagiIcons.House, strings(R.string.ui_go_home), Modifier.size(22.dp))
            }
            IconButton(onClick = onBack, enabled = canGoBack, modifier = Modifier.weight(1f).fillMaxHeight()) {
                Icon(NagiIcons.ArrowBack, strings(R.string.ui_back), Modifier.size(21.dp))
            }
            IconButton(onClick = onNewTab, modifier = Modifier.weight(1f).fillMaxHeight()) {
                Icon(NagiIcons.Plus, strings(R.string.ui_new_tab), Modifier.size(23.dp))
            }
            if (onSearch != null) IconButton(onClick = onSearch, modifier = Modifier.weight(1f).fillMaxHeight().testTag("compact-search-button")) {
                Icon(NagiIcons.Search, strings(R.string.ui_search_or_enter_url), Modifier.size(21.dp))
            }
            CompactTabsButton(tabCount, onTabs, Modifier.weight(1f).fillMaxHeight())
            IconButton(onClick = onPageMenu, modifier = Modifier.weight(1f).fillMaxHeight().testTag(pageMenuTag)) {
                Icon(NagiIcons.Ellipsis, strings(R.string.ui_page_menu), Modifier.size(21.dp).rotate(90f))
            }
        }
    }
}

@Composable
internal fun CompactTabsButton(tabCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val strings = rememberNagiStrings()
    IconButton(onClick = onClick, modifier = modifier.size(LocalNagiUiDimensions.current.controlSize)
        .testTag("compact-tabs-button").semantics {
            contentDescription = strings(R.string.ui_tabs)
            stateDescription = tabCount.toString()
        }) {
        Box(Modifier.size(24.dp).border(2.dp, LocalContentColor.current, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center) {
            Text(tabCount.toString(), style = MaterialTheme.typography.labelMedium)
        }
    }
}
