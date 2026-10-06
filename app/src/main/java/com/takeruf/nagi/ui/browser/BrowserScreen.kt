package com.takeruf.nagi.ui.browser

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.takeruf.nagi.browser.tabs.BrowserSessionController
import com.takeruf.nagi.ui.splitview.SplitLayout

@Composable
fun BrowserScreen(state: BrowserUiState, vm: BrowserViewModel, sessions: BrowserSessionController,
    rightTabId: String?, rightFocused: Boolean, onFocusRight: (Boolean) -> Unit,
    splitRatio: Float, onSplitRatio: (Float) -> Unit, showFind: Boolean, onFind: (Boolean) -> Unit,
    onOmnibox: () -> Unit, onOpenUrl: (String) -> Unit, onSplit: () -> Unit,
    onCloseSplit: () -> Unit, onSwap: () -> Unit,
    leftTabId: String? = null) {
    val left = state.visibleTabs.firstOrNull { it.id == leftTabId } ?: state.activeTab ?: return
    val right = state.visibleTabs.firstOrNull { it.id == rightTabId && it.id != left.id }
    val generation by sessions.generation.collectAsState()
    val pair = remember(left.id, right?.id, sessions, generation) {
        sessions.pool.setVisible(setOfNotNull(left.id, right?.id))
        sessions.acquire(left, state.settings.desktopDefault) to right?.let { sessions.acquire(it, state.settings.desktopDefault) }
    }
    DisposableEffect(Unit) { onDispose { sessions.pool.setVisible(emptySet()) } }
    @Composable
    fun Pane(isRight: Boolean) {
        val tab = if (isRight) right!! else left
        val engine = if (isRight) pair.second!! else pair.first
        Box(Modifier.fillMaxSize().testTag(if (isRight) "browser-pane-right-${tab.id}" else "browser-pane-left-${tab.id}")) {
            BrowserPane(tab, engine, state, focused = right == null || rightFocused == isRight,
                split = right != null, showFind = showFind, onCloseFind = { onFind(false) }, onFind = { onFind(true) },
                onFocus = { onFocusRight(isRight); vm.selectTab(tab.id) }, onOmnibox = onOmnibox, onOpen = onOpenUrl,
                onSplit = onSplit, onCloseSplit = onCloseSplit, onSwap = onSwap,
                onCloseTab = {
                    val remaining = if (isRight) left else right
                    if (remaining != null) vm.closeSplitTab(tab.id, remaining.id, onCloseSplit)
                })
        }
    }
    Box(Modifier.fillMaxSize()) {
        if (right == null) Pane(false)
        else SplitLayout(splitRatio, onSplitRatio, left = { Pane(false) }, right = { Pane(true) })
    }
}
