package com.orbit.browser.ui.browser

import com.orbit.browser.R
import com.orbit.browser.ui.localization.rememberOrbitStrings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import com.orbit.browser.ui.components.OrbitIcons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbit.browser.browser.engine.BrowserEngine
import com.orbit.browser.domain.model.BrowserTab
import com.orbit.browser.ui.components.ToolButton

@Composable
fun BrowserPane(tab: BrowserTab, engine: BrowserEngine, state: BrowserUiState, focused: Boolean,
    split: Boolean, showFind: Boolean, onCloseFind: () -> Unit, onFind: () -> Unit, onFocus: () -> Unit,
    onOmnibox: () -> Unit, onOpen: (String) -> Unit, onBookmark: (Boolean) -> Unit,
    onSplit: () -> Unit, onCloseSplit: () -> Unit, onSwap: () -> Unit, onPin: () -> Unit,
    onSelectRight: (String) -> Unit, rightPane: Boolean) {
    val strings = rememberOrbitStrings()
    val page by engine.state.collectAsStateWithLifecycle()
    var pageMenu by remember { mutableStateOf(false) }
    var rightMenu by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface,
        border = if (split && focused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxSize()) {
        Column {
            BoxWithConstraints {
            val compact = maxWidth < 320.dp
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                ToolButton(OrbitIcons.ArrowBack, strings(R.string.ui_back), page.canGoBack) { onFocus(); engine.goBack() }
                if (!split && !compact) ToolButton(OrbitIcons.ArrowForward, strings(R.string.ui_forward), page.canGoForward) { onFocus(); engine.goForward() }
                if (!compact) ToolButton(OrbitIcons.RotateCw, strings(R.string.ui_reload)) { onFocus(); engine.reload() }
                Surface(onClick = { onFocus(); onOmnibox() }, shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.weight(1f).height(40.dp)) {
                    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (page.url.startsWith("https://")) OrbitIcons.Lock else OrbitIcons.Search, null, Modifier.size(16.dp))
                        Text(if (page.url == "about:blank") (if (compact) strings(R.string.ui_search) else strings(R.string.ui_search_or_enter_url)) else page.url.removePrefix("https://").removePrefix("http://").removeSuffix("/"),
                            Modifier.weight(1f).padding(start = 10.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Box {
                    ToolButton(OrbitIcons.Ellipsis, strings(R.string.ui_page_menu)) { onFocus(); pageMenu = true }
                    DropdownMenu(pageMenu, { pageMenu = false }) {
                        if (compact) DropdownMenuItem(leadingIcon = { Icon(OrbitIcons.RotateCw, null) }, text = { Text(strings(R.string.ui_reload)) }, onClick = { engine.reload(); pageMenu = false })
                        DropdownMenuItem(leadingIcon = { Icon(OrbitIcons.Bookmark, null) }, text = { Text(strings(R.string.ui_save_bookmark)) }, enabled = page.url != "about:blank", onClick = { onBookmark(false); pageMenu = false })
                        DropdownMenuItem(leadingIcon = { Icon(OrbitIcons.Star, null) }, text = { Text(strings(R.string.ui_add_to_favorites)) }, enabled = page.url != "about:blank", onClick = { onBookmark(true); pageMenu = false })
                        DropdownMenuItem(leadingIcon = { Icon(if (tab.isPinned) OrbitIcons.PinOff else OrbitIcons.Pin, null) }, text = { Text(if (tab.isPinned) strings(R.string.ui_unpin_from_sidebar) else strings(R.string.ui_pin_to_sidebar)) }, onClick = { onPin(); pageMenu = false })
                        DropdownMenuItem(leadingIcon = { Icon(if (page.desktopMode) OrbitIcons.Smartphone else OrbitIcons.Monitor, null) }, text = { Text(if (page.desktopMode) strings(R.string.ui_use_mobile_site) else strings(R.string.ui_use_desktop_site)) }, onClick = { engine.setDesktopMode(!page.desktopMode); pageMenu = false })
                        DropdownMenuItem(leadingIcon = { Icon(OrbitIcons.Search, null) }, text = { Text(strings(R.string.ui_find_in_page)) }, onClick = { onFind(); pageMenu = false })
                        DropdownMenuItem(leadingIcon = { Icon(OrbitIcons.ArrowForward, null) }, text = { Text(strings(R.string.ui_forward)) }, enabled = page.canGoForward, onClick = { engine.goForward(); pageMenu = false })
                        if (!split) DropdownMenuItem(leadingIcon = { Icon(OrbitIcons.Columns2, null) }, text = { Text(strings(R.string.ui_new_split_view)) }, onClick = { onSplit(); pageMenu = false })
                        else {
                            DropdownMenuItem(leadingIcon = { Icon(OrbitIcons.ArrowLeftRight, null) }, text = { Text(strings(R.string.ui_swap_panes)) }, onClick = { onSwap(); pageMenu = false })
                            DropdownMenuItem(leadingIcon = { Icon(OrbitIcons.X, null) }, text = { Text(strings(R.string.ui_close_split_view)) }, onClick = { onCloseSplit(); pageMenu = false })
                        }
                    }
                }
            }
            }
            if (split) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(strings.tabTitle(tab), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (rightPane) Box {
                    TextButton(onClick = { rightMenu = true }) { Text(strings(R.string.ui_choose_tab), style = MaterialTheme.typography.labelSmall) }
                    DropdownMenu(rightMenu, { rightMenu = false }) {
                        state.visibleTabs.filter { it.id != state.activeTab?.id }.forEach { value -> DropdownMenuItem(text = { Text(strings.tabTitle(value), maxLines = 1) },
                            onClick = { onSelectRight(value.id); rightMenu = false }) }
                    }
                }
            }
            if (page.isLoading) LinearProgressIndicator(progress = { page.progress / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp))
            else HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (showFind && focused) {
                var query by remember { mutableStateOf("") }
                LaunchedEffect(query) { engine.findInPage(query) }
                DisposableEffect(engine) { onDispose { engine.clearFind() } }
                FindInPageBar(query, { query = it }, "${if (page.findMatches == 0) 0 else page.activeFindMatch + 1}/${page.findMatches}",
                    onPrevious = { engine.findNext(false) }, onNext = { engine.findNext(true) }, onClose = onCloseFind)
            }
            Box(Modifier.fillMaxSize()) {
                if (page.url == "about:blank") NewTabPage(state.currentSpace?.let(strings::spaceName) ?: strings(R.string.ui_your_space), onOmnibox)
                else BrowserSurface(engine, Modifier.fillMaxSize().clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)), onFocus)
                page.error?.let { error ->
                    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(strings.translate(error), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { engine.reload() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)) { Text(strings(R.string.ui_retry)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FindInPageBar(query: String, onQuery: (String) -> Unit, count: String,
    onPrevious: () -> Unit, onNext: () -> Unit, onClose: () -> Unit) {
    val strings = rememberOrbitStrings()
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        @Composable fun Input(modifier: Modifier) {
            TextField(query, onQuery, placeholder = { Text(strings(R.string.ui_find_in_page), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true, modifier = modifier)
        }
        @Composable fun Navigation() {
            ToolButton(OrbitIcons.ChevronUp, strings(R.string.ui_previous_match), onClick = onPrevious)
            ToolButton(OrbitIcons.ChevronDown, strings(R.string.ui_next_match), onClick = onNext)
        }
        if (maxWidth < 360.dp) Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Input(Modifier.weight(1f))
                ToolButton(OrbitIcons.X, strings(R.string.ui_close_find), onClick = onClose)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(count, Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.labelSmall)
                Navigation()
            }
        } else Row(verticalAlignment = Alignment.CenterVertically) {
            Input(Modifier.weight(1f))
            Text(count, style = MaterialTheme.typography.labelSmall)
            Navigation()
            ToolButton(OrbitIcons.X, strings(R.string.ui_close_find), onClick = onClose)
        }
    }
}
