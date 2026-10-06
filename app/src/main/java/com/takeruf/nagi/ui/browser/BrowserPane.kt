package com.takeruf.nagi.ui.browser

import com.takeruf.nagi.ui.theme.NagiShapes
import com.takeruf.nagi.R
import com.takeruf.nagi.ui.localization.rememberNagiStrings

import kotlinx.coroutines.delay
import androidx.compose.ui.platform.testTag
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import com.takeruf.nagi.ui.components.NagiIcons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.takeruf.nagi.browser.engine.BrowserEngine
import com.takeruf.nagi.domain.model.BrowserTab
import com.takeruf.nagi.ui.components.ToolButton
import com.takeruf.nagi.ui.components.AddressActionButton
import com.takeruf.nagi.ui.components.NagiOverflowMenu
import com.takeruf.nagi.ui.components.NagiOverflowMenuItem

@Composable
fun BrowserPane(tab: BrowserTab, engine: BrowserEngine, state: BrowserUiState, focused: Boolean,
    split: Boolean, showFind: Boolean, onCloseFind: () -> Unit, onFind: () -> Unit, onFocus: () -> Unit,
    onOmnibox: () -> Unit, onOpen: (String) -> Unit,
    onSplit: () -> Unit, onCloseSplit: () -> Unit, onCloseTab: () -> Unit, onSwap: () -> Unit) {
    val strings = rememberNagiStrings()
    val page by engine.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val canShareLink = page.url.isNotBlank() && page.url != "about:blank"
    var linkCopied by remember(page.url) { mutableStateOf(false) }
    LaunchedEffect(linkCopied) { if (linkCopied) { delay(1500); linkCopied = false } }
    val copyLink = {
        onFocus()
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(
            ClipData.newPlainText(page.title, page.url))
        linkCopied = true
        // Android 13 and later show their own clipboard confirmation.
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, strings(R.string.ui_link_copied), Toast.LENGTH_SHORT).show()
    }
    val shareLink = {
        onFocus()
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, page.url)
            putExtra(Intent.EXTRA_SUBJECT, page.title)
        }, strings(R.string.ui_share_link)))
    }
    var pageMenu by remember { mutableStateOf(false) }
    Surface(shape = NagiShapes.Rounded, color = MaterialTheme.colorScheme.surface,
        border = if (split && focused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxSize()) {
        Column {
            BoxWithConstraints {
            val compact = maxWidth < 320.dp
            val inlineLinkActions = maxWidth >= 360.dp
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                ToolButton(NagiIcons.ArrowBack, strings(R.string.ui_back), page.canGoBack) { onFocus(); engine.goBack() }
                if (!split && !compact) ToolButton(NagiIcons.ArrowForward, strings(R.string.ui_forward), page.canGoForward) { onFocus(); engine.goForward() }
                if (!compact) ToolButton(NagiIcons.RotateCw, strings(R.string.ui_reload)) { onFocus(); engine.reload() }
                Surface(onClick = { onFocus(); onOmnibox() }, shape = NagiShapes.Rounded,
                    color = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.weight(1f).height(40.dp)
                        .testTag("url-drop-${tab.id}")) {
                    Box {
                    WebUrlDropTarget(Modifier.matchParentSize()) { url -> onFocus(); engine.loadUrl(url) }
                    Row(Modifier.fillMaxSize().padding(start = 12.dp, end = if (inlineLinkActions) 4.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (page.url.startsWith("https://")) NagiIcons.Lock else NagiIcons.Search, null, Modifier.size(16.dp))
                        Text(if (page.url == "about:blank") (if (compact) strings(R.string.ui_search) else strings(R.string.ui_search_or_enter_url)) else page.url.removePrefix("https://").removePrefix("http://").removeSuffix("/"),
                            Modifier.weight(1f).padding(start = 10.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (inlineLinkActions) {
                            AddressActionButton(onClick = copyLink, enabled = canShareLink, modifier = Modifier.testTag("address-copy-url:${tab.id}")) {
                                Icon(if (linkCopied) NagiIcons.Check else NagiIcons.Link,
                                    strings(if (linkCopied) R.string.ui_link_copied else R.string.ui_copy_link), Modifier.size(16.dp))
                            }
                            AddressActionButton(onClick = shareLink, enabled = canShareLink) {
                                Icon(NagiIcons.Share, strings(R.string.ui_share_link), Modifier.size(16.dp))
                            }
                        }
                    }
                    }
                }
                Box {
                    ToolButton(NagiIcons.Ellipsis, strings(R.string.ui_page_menu)) { onFocus(); pageMenu = true }
                    NagiOverflowMenu(pageMenu, { pageMenu = false }) {
                        if (!inlineLinkActions) {
                            NagiOverflowMenuItem(leadingIcon = { Icon(NagiIcons.Link, null) }, text = { Text(strings(R.string.ui_copy_link)) }, enabled = canShareLink, onClick = { pageMenu = false; copyLink() })
                            NagiOverflowMenuItem(leadingIcon = { Icon(NagiIcons.Share, null) }, text = { Text(strings(R.string.ui_share_link)) }, enabled = canShareLink, onClick = { pageMenu = false; shareLink() })
                        }
                        if (compact) NagiOverflowMenuItem(leadingIcon = { Icon(NagiIcons.RotateCw, null) }, text = { Text(strings(R.string.ui_reload)) }, onClick = { engine.reload(); pageMenu = false })
                        NagiOverflowMenuItem(leadingIcon = { Icon(if (page.desktopMode) NagiIcons.Smartphone else NagiIcons.Monitor, null) }, text = { Text(if (page.desktopMode) strings(R.string.ui_use_mobile_site) else strings(R.string.ui_use_desktop_site)) }, onClick = { engine.setDesktopMode(!page.desktopMode); pageMenu = false })
                        NagiOverflowMenuItem(leadingIcon = { Icon(NagiIcons.Search, null) }, text = { Text(strings(R.string.ui_find_in_page)) }, onClick = { onFind(); pageMenu = false })
                        NagiOverflowMenuItem(leadingIcon = { Icon(NagiIcons.ArrowForward, null) }, text = { Text(strings(R.string.ui_forward)) }, enabled = page.canGoForward, onClick = { engine.goForward(); pageMenu = false })
                        if (!split) NagiOverflowMenuItem(leadingIcon = { Icon(NagiIcons.Columns2, null) }, text = { Text(strings(R.string.ui_new_split_view)) }, onClick = { onSplit(); pageMenu = false })
                        else {
                            NagiOverflowMenuItem(leadingIcon = { Icon(NagiIcons.ArrowLeftRight, null) }, text = { Text(strings(R.string.ui_swap_panes)) }, onClick = { onSwap(); pageMenu = false })
                            NagiOverflowMenuItem(leadingIcon = { Icon(NagiIcons.X, null) }, text = { Text(strings(R.string.ui_close_split_view)) }, onClick = { onCloseSplit(); pageMenu = false })
                        }
                    }
                }
            }
            }
            // Keep page titles aligned across both split panes.
            if (split) Row(Modifier.fillMaxWidth().height(48.dp).padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(strings.tabTitle(tab), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(onClick = onCloseTab, modifier = Modifier.size(48.dp).testTag("close-split-tab:${tab.id}")) {
                    Icon(NagiIcons.X, strings(R.string.ui_close_1_s, strings.tabTitle(tab)), Modifier.size(21.dp))
                }
            }
            Box(Modifier.fillMaxWidth().height(2.dp), contentAlignment = Alignment.BottomCenter) {
                if (page.isLoading) LinearProgressIndicator(progress = { page.progress / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp))
                else HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (showFind && focused) {
                var query by remember { mutableStateOf("") }
                LaunchedEffect(query) { engine.findInPage(query) }
                DisposableEffect(engine) { onDispose { engine.clearFind() } }
                FindInPageBar(query, { query = it }, "${if (page.findMatches == 0) 0 else page.activeFindMatch + 1}/${page.findMatches}",
                    onPrevious = { engine.findNext(false) }, onNext = { engine.findNext(true) }, onClose = onCloseFind)
            }
            Box(Modifier.fillMaxSize()) {
                if (page.url == "about:blank") Box(Modifier.fillMaxSize()) {
                    WebUrlDropTarget(Modifier.matchParentSize()) { url -> onFocus(); engine.loadUrl(url) }
                    NewTabPage(state.currentSpace?.let(strings::spaceName) ?: strings(R.string.ui_your_space), onOmnibox)
                }
                else BrowserSurface(engine, Modifier.fillMaxSize().clip(NagiShapes.Bottom), onFocus)
                page.error?.let { error ->
                    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(strings.translate(error), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            TextButton(shape = NagiShapes.Rounded, onClick = { engine.reload() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)) { Text(strings(R.string.ui_retry)) }
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
    val strings = rememberNagiStrings()
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        @Composable fun Input(modifier: Modifier) {
            TextField(query, onQuery, shape = NagiShapes.Rounded, placeholder = { Text(strings(R.string.ui_find_in_page), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true, modifier = modifier)
        }
        @Composable fun Navigation() {
            ToolButton(NagiIcons.ChevronUp, strings(R.string.ui_previous_match), onClick = onPrevious)
            ToolButton(NagiIcons.ChevronDown, strings(R.string.ui_next_match), onClick = onNext)
        }
        if (maxWidth < 360.dp) Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Input(Modifier.weight(1f))
                ToolButton(NagiIcons.X, strings(R.string.ui_close_find), onClick = onClose)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(count, Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.labelSmall)
                Navigation()
            }
        } else Row(verticalAlignment = Alignment.CenterVertically) {
            Input(Modifier.weight(1f))
            Text(count, style = MaterialTheme.typography.labelSmall)
            Navigation()
            ToolButton(NagiIcons.X, strings(R.string.ui_close_find), onClick = onClose)
        }
    }
}
