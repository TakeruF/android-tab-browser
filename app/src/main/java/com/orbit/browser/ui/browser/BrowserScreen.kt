package com.orbit.browser.ui.browser

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.orbit.browser.browser.tabs.BrowserSessionController
import com.orbit.browser.ui.splitview.SplitLayout

@Composable
fun BrowserScreen(state: BrowserUiState, vm: BrowserViewModel, sessions: BrowserSessionController,
    rightTabId: String?, rightFocused: Boolean, onFocusRight: (Boolean) -> Unit,
    splitRatio: Float, onSplitRatio: (Float) -> Unit, showFind: Boolean, onFind: (Boolean) -> Unit,
    onOmnibox: () -> Unit, onOpenUrl: (String) -> Unit, onSplit: () -> Unit,
    onCloseSplit: () -> Unit, onSwap: () -> Unit, onSelectRight: (String) -> Unit) {
    val left = state.activeTab ?: return
    val right = state.visibleTabs.firstOrNull { it.id == rightTabId && it.id != left.id }
    val pair = remember(left.id, right?.id, sessions) {
        sessions.pool.setVisible(setOfNotNull(left.id, right?.id))
        sessions.acquire(left, state.settings.desktopDefault) to right?.let { sessions.acquire(it, state.settings.desktopDefault) }
    }
    DisposableEffect(Unit) { onDispose { sessions.pool.setVisible(emptySet()) } }
    @Composable
    fun Pane(isRight: Boolean) {
        val tab = if (isRight) right!! else left
        val engine = if (isRight) pair.second!! else pair.first
        BrowserPane(tab, engine, state, focused = right == null || rightFocused == isRight,
            split = right != null, showFind = showFind, onCloseFind = { onFind(false) }, onFind = { onFind(true) },
            onFocus = { onFocusRight(isRight); vm.accessTab(tab.id) }, onOmnibox = onOmnibox, onOpen = onOpenUrl,
            onBookmark = { favorite -> vm.bookmark(tab.copy(url = engine.state.value.url,
                title = engine.state.value.title, faviconUrl = engine.state.value.faviconUrl), favorite) },
            onSplit = onSplit, onCloseSplit = onCloseSplit, onSwap = onSwap,
            onPin = { vm.togglePin(tab.id) }, onSelectRight = onSelectRight, rightPane = isRight)
    }
    Box(Modifier.fillMaxSize()) {
        if (right == null) Pane(false)
        else SplitLayout(splitRatio, onSplitRatio, left = { Pane(false) }, right = { Pane(true) })
    }
}
