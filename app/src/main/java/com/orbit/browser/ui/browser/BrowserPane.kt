package com.orbit.browser.ui.browser

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
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
                ToolButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", page.canGoBack) { onFocus(); engine.goBack() }
                if (!split && !compact) ToolButton(Icons.AutoMirrored.Outlined.ArrowForward, "Forward", page.canGoForward) { onFocus(); engine.goForward() }
                if (!compact) ToolButton(Icons.Outlined.Refresh, "Reload") { onFocus(); engine.reload() }
                Surface(onClick = { onFocus(); onOmnibox() }, shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.weight(1f).height(40.dp)) {
                    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (page.url.startsWith("https://")) Icons.Outlined.Lock else Icons.Outlined.Search, null, Modifier.size(16.dp))
                        Text(if (page.url == "about:blank") (if (compact) "Search" else "Search or enter URL") else page.url.removePrefix("https://").removePrefix("http://").removeSuffix("/"),
                            Modifier.weight(1f).padding(start = 10.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Box {
                    ToolButton(Icons.Outlined.MoreHoriz, "Page menu") { onFocus(); pageMenu = true }
                    DropdownMenu(pageMenu, { pageMenu = false }) {
                        if (compact) DropdownMenuItem(text = { Text("Reload") }, onClick = { engine.reload(); pageMenu = false })
                        DropdownMenuItem(text = { Text("Save bookmark") }, enabled = page.url != "about:blank", onClick = { onBookmark(false); pageMenu = false })
                        DropdownMenuItem(text = { Text("Add to favorites") }, enabled = page.url != "about:blank", onClick = { onBookmark(true); pageMenu = false })
                        DropdownMenuItem(text = { Text(if (tab.isPinned) "Unpin from sidebar" else "Pin to sidebar") }, onClick = { onPin(); pageMenu = false })
                        DropdownMenuItem(text = { Text(if (page.desktopMode) "Use mobile site" else "Use desktop site") }, onClick = { engine.setDesktopMode(!page.desktopMode); pageMenu = false })
                        DropdownMenuItem(text = { Text("Find in page") }, onClick = { onFind(); pageMenu = false })
                        DropdownMenuItem(text = { Text("Forward") }, enabled = page.canGoForward, onClick = { engine.goForward(); pageMenu = false })
                        if (!split) DropdownMenuItem(text = { Text("New split view") }, onClick = { onSplit(); pageMenu = false })
                        else {
                            DropdownMenuItem(text = { Text("Swap panes") }, onClick = { onSwap(); pageMenu = false })
                            DropdownMenuItem(text = { Text("Close split view") }, onClick = { onCloseSplit(); pageMenu = false })
                        }
                    }
                }
            }
            }
            if (split) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tab.title, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (rightPane) Box {
                    TextButton(onClick = { rightMenu = true }) { Text("Choose tab", style = MaterialTheme.typography.labelSmall) }
                    DropdownMenu(rightMenu, { rightMenu = false }) {
                        state.visibleTabs.filter { it.id != state.activeTab?.id }.forEach { value -> DropdownMenuItem(text = { Text(value.title, maxLines = 1) },
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
                if (page.url == "about:blank") NewTabPage(state.currentSpace?.name ?: "your Space", onOmnibox)
                else BrowserSurface(engine, Modifier.fillMaxSize().clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)), onFocus)
                page.error?.let { error ->
                    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(error, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { engine.reload() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)) { Text("Retry") }
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
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        @Composable fun Input(modifier: Modifier) {
            TextField(query, onQuery, placeholder = { Text("Find in page", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true, modifier = modifier)
        }
        @Composable fun Navigation() {
            ToolButton(Icons.Outlined.ExpandLess, "Previous match", onClick = onPrevious)
            ToolButton(Icons.Outlined.ExpandMore, "Next match", onClick = onNext)
        }
        if (maxWidth < 360.dp) Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Input(Modifier.weight(1f))
                ToolButton(Icons.Outlined.Close, "Close find", onClick = onClose)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(count, Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.labelSmall)
                Navigation()
            }
        } else Row(verticalAlignment = Alignment.CenterVertically) {
            Input(Modifier.weight(1f))
            Text(count, style = MaterialTheme.typography.labelSmall)
            Navigation()
            ToolButton(Icons.Outlined.Close, "Close find", onClick = onClose)
        }
    }
}
