package com.takeruf.nagi.ui.browser

import com.takeruf.nagi.ui.components.hardwareKeyboardConnected
import com.takeruf.nagi.ui.components.LocalNagiUiDimensions
import com.takeruf.nagi.ui.theme.NagiShapes
import com.takeruf.nagi.R
import com.takeruf.nagi.ui.localization.rememberNagiStrings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.takeruf.nagi.ui.components.NagiIcons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.takeruf.nagi.domain.model.BrowserSettings
import com.takeruf.nagi.domain.model.BrowserTab
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal data class NewTabShortcut(val tab: BrowserTab, val rightTab: BrowserTab? = null)

// Only the active workspace's open tabs are supplied. Wide layouts group a saved pair into one slot.
internal fun newTabShortcuts(tabs: List<BrowserTab>, currentTabId: String, compactWindow: Boolean = false): List<NewTabShortcut> {
    val byId = tabs.associateBy { it.id }
    val pairs = tabs.mapNotNull { left ->
        val right = byId[left.splitRightTabId]?.takeIf { it.id != left.id } ?: return@mapNotNull null
        NewTabShortcut(left, right)
    }
    val pairedIds = pairs.flatMap { listOfNotNull(it.tab.id, it.rightTab?.id) }.toSet()
    if (compactWindow) {
        return tabs.filter { it.id != currentTabId && (it.isPinned || it.id in pairedIds) }
            .take(4).map { NewTabShortcut(it) }
    }
    val savedSplits = pairs.filter { it.tab.id != currentTabId && it.rightTab?.id != currentTabId }
    val pinned = tabs.asSequence()
        .filter { it.isPinned && it.id != currentTabId && it.id !in pairedIds }
        .take(if (savedSplits.isEmpty()) 4 else 3)
        .map { NewTabShortcut(it) }
        .toList()
    return (pinned + savedSplits).take(4)
}

@Composable
internal fun NewTabPage(onSearch: () -> Unit, compactWindow: Boolean = false,
    settings: BrowserSettings = BrowserSettings(), onCustomize: () -> Unit = {},
    shortcuts: List<NewTabShortcut> = emptyList(), onResumeTab: (String) -> Unit = {},
    requestInitialFocus: Boolean = true, onSearchBounds: (androidx.compose.ui.geometry.Rect) -> Unit = {}) {
    val strings = rememberNagiStrings()
    val focus = remember { FocusRequester() }
    var pageFocused by remember { mutableStateOf(false) }
    LaunchedEffect(requestInitialFocus) {
        // A blank page has no native WebView to take focus. Keep Enter away from
        // the first toolbar button until the user explicitly navigates to it.
        if (requestInitialFocus) focus.requestFocus()
    }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("new-tab-page").focusRequester(focus)
        .onFocusChanged { pageFocused = it.isFocused }
        .onKeyEvent { pageFocused && it.key in setOf(Key.Enter, Key.NumPadEnter) }.focusable(),
        contentAlignment = Alignment.Center) {
        NewTabBackground(settings, Modifier.matchParentSize())
        val compact = compactWindow || maxWidth < 320.dp
        val dimensions = LocalNagiUiDimensions.current
        val content = Modifier.padding(bottom = if (shortcuts.isEmpty()) 0.dp else if (dimensions.compact) 48.dp else 64.dp)
            .padding(dimensions.gutter(!compact)).widthIn(max = 440.dp)
        Column(if (shortcuts.isEmpty()) content else content.verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(onClick = onSearch, modifier = Modifier.testTag("new-tab-search")
                .onGloballyPositioned { onSearchBounds(it.boundsInWindow()) },
                shape = NagiShapes.Pill, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface) {
                Row(Modifier.fillMaxWidth().height(dimensions.toolbarHeight).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(NagiIcons.Search, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (compact) strings(R.string.ui_search) else strings(R.string.ui_where_would_you_like_to_go), Modifier.weight(1f).padding(start = 12.dp),
                        style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!compact && hardwareKeyboardConnected()) Text("Ctrl L", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (shortcuts.isNotEmpty()) {
                Spacer(Modifier.height(if (dimensions.compact) 16.dp else 24.dp))
                Surface(shape = NagiShapes.Rounded, color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().testTag("new-tab-shortcuts")) {
                    Column {
                        Text(strings(R.string.ui_resume_browsing), Modifier.padding(start = 12.dp, top = 10.dp),
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        shortcuts.forEachIndexed { index, shortcut ->
                            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh)
                            val right = shortcut.rightTab
                            Surface(onClick = { onResumeTab(shortcut.tab.id) }, color = Color.Transparent,
                                modifier = Modifier.fillMaxWidth().testTag("new-tab-shortcut-${shortcut.tab.id}")) {
                                Row(Modifier.heightIn(min = if (dimensions.compact) 40.dp else 56.dp).padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(if (right != null) NagiIcons.Columns2 else if (shortcut.tab.isPinned) NagiIcons.Pin else NagiIcons.PanelsTopLeft,
                                        if (right != null) strings(R.string.ui_saved_split) else if (shortcut.tab.isPinned) strings(R.string.ui_pinned_tab) else null,
                                        Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                    Column(Modifier.weight(1f)) {
                                        Text(strings.tabTitle(shortcut.tab), style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (right != null) Text(strings.tabTitle(right), style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                                            overflow = TextOverflow.Ellipsis)
                                    }
                                    Icon(NagiIcons.ChevronRight, null, Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
        FilledTonalIconButton(onClick = onCustomize, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            .testTag("customize-new-tab")) {
            Icon(NagiIcons.Palette, strings(R.string.ui_new_tab_background))
        }
    }
}
