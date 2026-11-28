package com.takeruf.nagi.ui.browser

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.takeruf.nagi.ui.theme.NagiShapes
import com.takeruf.nagi.R
import com.takeruf.nagi.ui.localization.rememberNagiStrings

import kotlinx.coroutines.delay
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import com.takeruf.nagi.ui.components.NagiIcons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.takeruf.nagi.browser.engine.BrowserEngine
import com.takeruf.nagi.browser.engine.PageErrorKind
import com.takeruf.nagi.domain.model.BrowserTab
import com.takeruf.nagi.ui.components.ToolButton
import com.takeruf.nagi.ui.components.AddressActionButton
import com.takeruf.nagi.ui.components.NagiOverflowMenuItem
import com.takeruf.nagi.ui.components.LocalNagiMenuDimensions
import com.takeruf.nagi.ui.components.LocalNagiUiDimensions

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrowserPane(tab: BrowserTab, engine: BrowserEngine, state: BrowserUiState, focused: Boolean,
    split: Boolean, showFind: Boolean, onCloseFind: () -> Unit, onFind: () -> Unit, onFocus: () -> Unit,
    onOmnibox: () -> Unit, onOpen: (String) -> Unit,
    onSplit: () -> Unit, onCloseSplit: () -> Unit, onCloseTab: () -> Unit, onSwap: () -> Unit,
    onAdBlockingChange: (String, Boolean) -> Unit = { _, _ -> },
    onSiteDarkModeChange: (String, Boolean) -> Unit = { _, _ -> },
    onVisitBlockingChange: (Boolean) -> Unit = {}, onCustomizeNewTab: () -> Unit = {}, compactWindow: Boolean = false,
    onNewTab: () -> Unit = {}, onTabs: () -> Unit = {},
    onHistory: () -> Unit = {}, onSettings: () -> Unit = {},
    pageMenuState: BrowserPageMenuState = remember { BrowserPageMenuState() },
    onTranslationOptions: () -> Unit = {}, onResumeTab: (String) -> Unit = {},
    historyBackPreview: HistoryBackPreviewState? = null,
    onAddressBarBounds: (Rect) -> Unit = {},
    onNewTabSearch: (Rect?) -> Unit = { onOmnibox() }) {
    val strings = rememberNagiStrings()
    val page by engine.state.collectAsStateWithLifecycle()
    val previewScope = rememberCoroutineScope()
    val trackpadPreview = remember(engine) { TrackpadHistoryPreview(previewScope, engine) }
    DisposableEffect(trackpadPreview) { onDispose { trackpadPreview.dispose() } }
    var newTabSearchBounds by remember(tab.id) { mutableStateOf<Rect?>(null) }
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
    val pageMenu = pageMenuState.tabId == tab.id
    var pageMenuHostBounds by remember(tab.id) { mutableStateOf<Rect?>(null) }
    val siteHost = com.takeruf.nagi.browser.engine.SiteDisplayModeStore.siteHost(page.url)
    val darkTheme = state.settings.theme == com.takeruf.nagi.domain.model.ThemeMode.DARK ||
        state.settings.theme == com.takeruf.nagi.domain.model.ThemeMode.SYSTEM && isSystemInDarkTheme()
    val bottomSearchBar = compactWindow && state.settings.bottomSearchBar
    val uiDimensions = LocalNagiUiDimensions.current
    // Keep the native error document detached during retry so it cannot flash behind loading.
    var retainedError by remember(engine) { mutableStateOf<PageErrorKind?>(null) }
    val visibleError = if (page.error != null) page.errorKind else retainedError?.takeIf { page.isLoading }
    SideEffect { retainedError = visibleError }
    Surface(shape = if (compactWindow) RectangleShape else NagiShapes.Rounded, color = MaterialTheme.colorScheme.surface,
        border = if (split && focused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxSize()) {
        @Composable
        fun AddressBar() {
            BoxWithConstraints(Modifier.then(if (bottomSearchBar) Modifier.testTag("compact-browser-bar") else Modifier)) {
                val compact = compactWindow || maxWidth < 320.dp
                // Each address action takes 32dp; retain share, then copy, then the shield.
                val inlineShare = !compactWindow && maxWidth >= 296.dp
                val inlineCopy = !compactWindow && maxWidth >= 328.dp
                val inlineBlocking = !compactWindow && maxWidth >= 360.dp
                Row(Modifier.fillMaxWidth().height(uiDimensions.toolbarHeight).testTag("browser-address-bar-${tab.id}")
                    .onGloballyPositioned { if (bottomSearchBar) pageMenuState.bottomBarBounds = it.boundsInWindow() }
                    .then(if (bottomSearchBar) Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow) else Modifier)
                    .padding(start = if (bottomSearchBar) 8.dp else 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!compactWindow) ToolButton(NagiIcons.ArrowBack, strings(R.string.ui_back), page.canGoBack) { onFocus(); engine.goBack() }
                    if (bottomSearchBar) IconButton(onClick = { onFocus(); onCloseFind(); engine.loadUrl("about:blank") },
                        modifier = Modifier.size(uiDimensions.controlSize).testTag("compact-home-button")) {
                        Icon(NagiIcons.House, strings(R.string.ui_go_home), Modifier.size(22.dp))
                    }
                    if (!split && !compact) ToolButton(NagiIcons.ArrowForward, strings(R.string.ui_forward), page.canGoForward) { onFocus(); engine.goForward() }
                    if (!compact) ToolButton(NagiIcons.RotateCw, strings(R.string.ui_reload)) { onFocus(); engine.reload() }
                    Surface(onClick = { onFocus(); onOmnibox() }, shape = NagiShapes.Pill,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).height(if (bottomSearchBar) 44.dp else 40.dp)
                            .testTag("url-drop-${tab.id}")) {
                        // Measure the visible field, excluding Surface's larger touch target.
                        Box(Modifier.fillMaxSize().onGloballyPositioned { onAddressBarBounds(it.boundsInWindow()) }) {
                        WebUrlDropTarget(Modifier.matchParentSize(), shape = NagiShapes.Pill) { url -> onFocus(); engine.loadUrl(url) }
                        Row(Modifier.fillMaxSize().padding(start = if (bottomSearchBar) 16.dp else 14.dp, end = if (inlineShare) 6.dp else 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (!bottomSearchBar) Icon(if (page.url.startsWith("https://")) NagiIcons.Lock else NagiIcons.Search, null, Modifier.size(16.dp))
                            val displayUrl = if (bottomSearchBar) {
                                val uri = android.net.Uri.parse(page.url)
                                uri.host?.let { host -> host.removePrefix("www.") + if (uri.port >= 0) ":${uri.port}" else "" } ?: page.url
                            } else page.url.removePrefix("https://").removePrefix("http://").removeSuffix("/")
                            Text(if (page.url == "about:blank") (if (compact) strings(R.string.ui_search) else strings(R.string.ui_search_or_enter_url)) else displayUrl,
                                Modifier.weight(1f).padding(start = if (bottomSearchBar) 0.dp else 10.dp),
                                style = if (bottomSearchBar) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (inlineBlocking) ContentBlockingShield(page, state.settings, onFocus = onFocus,
                                onPauseVisit = { paused ->
                                    onVisitBlockingChange(paused)
                                    engine.reload()
                                }, onSiteBlockingChange = onAdBlockingChange)
                            if (inlineCopy) {
                                AddressActionButton(onClick = copyLink, enabled = canShareLink, modifier = Modifier.testTag("address-copy-url:${tab.id}")) {
                                    Icon(if (linkCopied) NagiIcons.Check else NagiIcons.Link,
                                        strings(if (linkCopied) R.string.ui_link_copied else R.string.ui_copy_link), Modifier.size(16.dp))
                                }
                            }
                            if (inlineShare) {
                                AddressActionButton(onClick = shareLink, enabled = canShareLink, modifier = Modifier.testTag("address-share-url:${tab.id}")) {
                                    Icon(NagiIcons.Share, strings(R.string.ui_share_link), Modifier.size(16.dp))
                                }
                            }
                        }
                        }
                    }
                    if (bottomSearchBar) {
                        IconButton(onClick = onNewTab, modifier = Modifier.size(uiDimensions.controlSize).testTag("compact-new-tab-button")) {
                            Icon(NagiIcons.Plus, strings(R.string.ui_new_tab), Modifier.size(24.dp))
                        }
                        CompactTabsButton(state.visibleTabs.size, onTabs)
                    }
                    Box(Modifier.onGloballyPositioned {
                        // The top-address layout has a zero-size popup host; keep its origin even then.
                        val origin = it.localToWindow(androidx.compose.ui.geometry.Offset.Zero)
                        pageMenuHostBounds = Rect(origin.x, origin.y,
                            origin.x + it.size.width, origin.y + it.size.height)
                    }) {
                        if (!compactWindow || bottomSearchBar) IconButton(onClick = { onFocus(); pageMenuState.toggle(tab.id) }, modifier = Modifier.size(uiDimensions.controlSize).testTag("page-menu-${tab.id}")) {
                            Icon(NagiIcons.Ellipsis, strings(R.string.ui_page_menu), Modifier.size(21.dp)
                                .then(if (compactWindow) Modifier.rotate(90f) else Modifier))
                        }
                        BrowserPageMenu(pageMenu, compactWindow, { pageMenuState.tabId = null },
                            bottomBarBounds = pageMenuState.bottomBarBounds, hostBounds = pageMenuHostBounds, actions = {
                            val dimensions = LocalNagiMenuDimensions.current
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                // Divide whole pixels so rounding cannot squeeze the last button.
                                val buttonSize = minOf(dimensions.rowHeight,
                                    with(LocalDensity.current) { (constraints.maxWidth / 5).toDp() })
                                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides buttonSize) {
                                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("compact-page-actions"),
                                        horizontalArrangement = Arrangement.SpaceBetween) {
                                        @Composable
                                        fun Action(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String,
                                            enabled: Boolean = true, tag: String, action: () -> Unit) {
                                            FilledTonalIconButton(onClick = { onFocus(); pageMenuState.tabId = null; action() }, enabled = enabled,
                                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                                                modifier = Modifier.size(buttonSize).testTag(tag)) {
                                                Icon(icon, label, Modifier.size(dimensions.iconSize))
                                            }
                                        }
                                        Action(NagiIcons.ArrowBack, strings(R.string.ui_back), page.canGoBack || page.readerArticle != null,
                                            "menu-back") { engine.goBack() }
                                        Action(NagiIcons.ArrowForward, strings(R.string.ui_forward), page.canGoForward,
                                            "menu-forward") { engine.goForward() }
                                        Action(NagiIcons.Link, strings(R.string.ui_copy_link), canShareLink,
                                            "menu-copy-url:${tab.id}", copyLink)
                                        Action(NagiIcons.Share, strings(R.string.ui_share_link), canShareLink,
                                            "menu-share-url:${tab.id}", shareLink)
                                        Action(NagiIcons.RotateCw, strings(R.string.ui_reload), canShareLink,
                                            "menu-reload") { engine.reload() }
                                    }
                                }
                            }
                        }) {
                            val dimensions = LocalNagiMenuDimensions.current
                            if (engine.supportsPageZoom && canShareLink && page.readerArticle == null) {
                                val zoomEnabled = !page.isLoading && !page.readerLoading && page.error == null
                                Row(Modifier.fillMaxWidth().heightIn(min = dimensions.rowHeight).padding(start = dimensions.horizontalPadding), verticalAlignment = Alignment.CenterVertically) {
                                    Text(strings(R.string.ui_page_zoom), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    IconButton(onClick = { onFocus(); engine.zoomOut() }, enabled = zoomEnabled && page.canZoomOut,
                                        modifier = Modifier.size(dimensions.rowHeight).testTag("page-zoom-out")) {
                                        Text("−", style = MaterialTheme.typography.headlineSmall,
                                            modifier = Modifier.semantics { contentDescription = strings(R.string.ui_page_zoom_out) })
                                    }
                                    TextButton(onClick = { onFocus(); engine.resetZoom() }, enabled = zoomEnabled && page.pageZoomPercent != 100,
                                        shape = NagiShapes.Rounded, contentPadding = PaddingValues(horizontal = 8.dp),
                                        modifier = Modifier.widthIn(min = 64.dp).heightIn(min = dimensions.rowHeight).testTag("page-zoom-reset")
                                            .semantics { contentDescription = strings(R.string.ui_page_zoom_reset) }) {
                                        Text(java.text.NumberFormat.getPercentInstance().format(page.pageZoomPercent / 100.0))
                                    }
                                    IconButton(onClick = { onFocus(); engine.zoomIn() }, enabled = zoomEnabled && page.canZoomIn,
                                        modifier = Modifier.size(dimensions.rowHeight).testTag("page-zoom-in")) {
                                        Icon(NagiIcons.Plus, strings(R.string.ui_page_zoom_in), Modifier.size(dimensions.iconSize))
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            if (!compactWindow && !inlineShare) {
                                NagiOverflowMenuItem(leadingIcon = { Icon(NagiIcons.Share, null) }, text = { Text(strings(R.string.ui_share_link)) }, enabled = canShareLink,
                                    modifier = Modifier.testTag("menu-share-url:${tab.id}"), onClick = { pageMenuState.tabId = null; shareLink() })
                            }
                            if (!compactWindow && !inlineCopy) {
                                NagiOverflowMenuItem(leadingIcon = { Icon(NagiIcons.Link, null) }, text = { Text(strings(R.string.ui_copy_link)) }, enabled = canShareLink,
                                    modifier = Modifier.testTag("menu-copy-url:${tab.id}"), onClick = { pageMenuState.tabId = null; copyLink() })
                            }
                            if (!inlineBlocking) {
                                val siteHost = com.takeruf.nagi.browser.engine.SiteDisplayModeStore.siteHost(page.url)
                                val siteBlocking = state.settings.adBlockingEnabled && siteHost !in state.settings.adBlockExcludedHosts
                                NagiOverflowMenuItem(leadingIcon = { Icon(NagiIcons.ShieldCheck, null) },
                                    text = { Text(strings(if (siteBlocking) R.string.ui_disable_site_blocking else R.string.ui_enable_site_blocking, page.blockedRequests)) },
                                    enabled = siteHost != null && state.settings.adBlockingEnabled,
                                    modifier = Modifier.testTag("site-ad-blocking"),
                                    onClick = { siteHost?.let { onAdBlockingChange(it, !siteBlocking) }; pageMenuState.tabId = null })
                            }
                            if (compact && !compactWindow) NagiOverflowMenuItem(leadingIcon = { Icon(NagiIcons.RotateCw, null) },
                                text = { Text(strings(R.string.ui_reload)) }, modifier = Modifier.testTag("menu-reload"),
                                onClick = { engine.reload(); pageMenuState.tabId = null })
                            if (engine.supportsPasswordAutofill)
                                NagiOverflowMenuItem(leadingIcon = { Icon(NagiIcons.KeyRound, null) },
                                    text = { Text(strings(R.string.ui_fill_saved_password)) },
                                    modifier = Modifier.testTag("request-password-autofill"),
                                    enabled = !page.isLoading && !page.readerLoading && page.readerArticle == null && page.error == null &&
                                        (page.url.startsWith("https://") || page.url.startsWith("http://")),
                                    onClick = { pageMenuState.tabId = null; engine.requestPasswordAutofill() })
                            NagiOverflowMenuItem(leadingIcon = { Icon(NagiIcons.Languages, null) }, text = { Text(strings(R.string.ui_translate_page)) },
                                modifier = Modifier.testTag("translate-page"), enabled = !page.isLoading && !page.readerLoading && page.error == null &&
                                    (page.url.startsWith("https://") || page.url.startsWith("http://")),
                                onClick = { onFocus(); pageMenuState.tabId = null; onTranslationOptions() })
                            if (page.translation.active) NagiOverflowMenuItem(leadingIcon = { Icon(NagiIcons.RotateCw, null) },
                                text = { Text(strings(R.string.ui_translation_original)) }, modifier = Modifier.testTag("menu-translation-restore"),
                                onClick = { pageMenuState.tabId = null; engine.restoreOriginalPage() })
                            NagiOverflowMenuItem(leadingIcon = { Icon(if (!page.desktopMode) NagiIcons.Check else NagiIcons.Smartphone, null) },
                                text = { Text(strings(R.string.ui_use_mobile_site)) }, enabled = page.desktopMode,
                                modifier = Modifier.testTag("display-mode:mobile").semantics { selected = !page.desktopMode },
                                onClick = { engine.setDesktopMode(false); pageMenuState.tabId = null })
                            NagiOverflowMenuItem(leadingIcon = { Icon(if (page.desktopMode) NagiIcons.Check else NagiIcons.Monitor, null) },
                                text = { Text(strings(R.string.ui_use_desktop_site)) }, enabled = !page.desktopMode,
                                modifier = Modifier.testTag("display-mode:desktop").semantics { selected = page.desktopMode },
                                onClick = { engine.setDesktopMode(true); pageMenuState.tabId = null })
                            if (darkTheme && state.settings.adaptiveDarkMode && engine.supportsPageDarkening &&
                                siteHost != null && page.readerArticle == null) {
                                val siteDarkMode = siteHost !in state.settings.darkModeExcludedHosts
                                NagiOverflowMenuItem(leadingIcon = { Icon(NagiIcons.Moon, null) },
                                    text = { Text(strings(if (siteDarkMode) R.string.ui_disable_site_dark_mode else R.string.ui_enable_site_dark_mode)) },
                                    modifier = Modifier.testTag("site-dark-mode"),
                                    onClick = { onSiteDarkModeChange(siteHost, !siteDarkMode); pageMenuState.tabId = null })
                            }
                            NagiOverflowMenuItem(leadingIcon = { Icon(NagiIcons.BookOpen, null) },
                                text = { Text(strings(if (page.readerArticle != null) R.string.ui_exit_reader else R.string.ui_reader_mode)) },
                                modifier = Modifier.testTag("reader-mode"), enabled = canShareLink && !page.isLoading && !page.readerLoading,
                                onClick = { engine.toggleReader(); pageMenuState.tabId = null })
                            if ((context as? com.takeruf.nagi.MainActivity)?.isPrivateBrowsing != true)
                                NagiOverflowMenuItem(leadingIcon = { Icon(NagiIcons.Lock, null) }, text = { Text(strings(R.string.ui_private_mode)) },
                                    modifier = Modifier.testTag("open-private-mode"),
                                    onClick = { (context as? com.takeruf.nagi.MainActivity)?.openPrivateBrowsing(); pageMenuState.tabId = null })
                            NagiOverflowMenuItem(leadingIcon = { Icon(NagiIcons.Search, null) }, text = { Text(strings(R.string.ui_find_in_page)) }, onClick = { onFind(); pageMenuState.tabId = null })
                            // Compact keeps history controls in the fixed bottom action row.
                            if (!compactWindow && (split || compact)) NagiOverflowMenuItem(leadingIcon = { Icon(NagiIcons.ArrowForward, null) }, text = { Text(strings(R.string.ui_forward)) }, modifier = Modifier.testTag("menu-forward"), enabled = page.canGoForward, onClick = { engine.goForward(); pageMenuState.tabId = null })
                            if (compactWindow) {
                                com.takeruf.nagi.ui.components.NagiOverflowMenuDivider()
                                NagiOverflowMenuItem(leadingIcon = { Icon(NagiIcons.Plus, null) }, text = { Text(strings(R.string.ui_new_tab)) },
                                    onClick = { pageMenuState.tabId = null; onNewTab() })
                                NagiOverflowMenuItem(leadingIcon = { Icon(NagiIcons.History, null) }, text = { Text(strings(R.string.ui_history)) },
                                    onClick = { pageMenuState.tabId = null; onHistory() })
                                NagiOverflowMenuItem(leadingIcon = { Icon(NagiIcons.Settings, null) }, text = { Text(strings(R.string.ui_settings)) },
                                    onClick = { pageMenuState.tabId = null; onSettings() })
                            }
                            if (!compactWindow && !split) NagiOverflowMenuItem(leadingIcon = { Icon(NagiIcons.Columns2, null) }, text = { Text(strings(R.string.ui_new_split_view)) }, onClick = { onSplit(); pageMenuState.tabId = null })
                            else if (!compactWindow) {
                                NagiOverflowMenuItem(leadingIcon = { Icon(NagiIcons.ArrowLeftRight, null) }, text = { Text(strings(R.string.ui_swap_panes)) }, onClick = { onSwap(); pageMenuState.tabId = null })
                                NagiOverflowMenuItem(leadingIcon = { Icon(NagiIcons.X, null) }, text = { Text(strings(R.string.ui_close_split_view)) }, onClick = { onCloseSplit(); pageMenuState.tabId = null })
                            }
                        }
                    }
                }
            }
        }
        Column {
            if (!bottomSearchBar) AddressBar()
            // Keep page titles aligned across both split panes.
            if (split) Row(Modifier.fillMaxWidth().height(uiDimensions.controlSize).padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(strings.tabTitle(tab), Modifier.weight(1f).testTag("split-title-${tab.id}").semantics { selected = focused },
                    style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onCloseTab, modifier = Modifier.size(uiDimensions.controlSize).testTag("close-split-tab:${tab.id}")) {
                    Icon(NagiIcons.X, strings(R.string.ui_close_1_s, strings.tabTitle(tab)), Modifier.size(21.dp))
                }
            }
            if (!bottomSearchBar) Box(Modifier.fillMaxWidth().height(2.dp), contentAlignment = Alignment.BottomCenter) {
                if (page.isLoading) LinearProgressIndicator(progress = { page.progress / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp))
                else HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
            }
            val findOpen = showFind && focused
            var findQuery by remember(findOpen) { mutableStateOf("") }
            if (findOpen) {
                LaunchedEffect(findQuery, page.readerArticle) { engine.findInPage(findQuery) }
                DisposableEffect(engine) { onDispose { engine.clearFind() } }
            }
            PageTranslationBar(page.translation, onRestore = engine::restoreOriginalPage)
            key(tab.id, engine) {
                GoogleSignInRecoveryBar(page, onSwitchToMobile = { onFocus(); engine.retryGoogleSignInInMobileMode() })
                HumanVerificationRecoveryBar(page,
                    onRetry = { onFocus(); engine.setDesktopMode(false) })
            }
            HistoryBackPreview(if (historyBackPreview?.engine === engine) historyBackPreview else trackpadPreview.state, engine,
                Modifier.fillMaxWidth().weight(1f).testTag("browser-content-${tab.id}"),
                newTab = { NewTabPage({}, compactWindow, state.settings, {}, requestInitialFocus = false) }) {
              Box(Modifier.fillMaxSize()) {
                if (visibleError != null) BrowserErrorPage(visibleError, page.isLoading, onFocus) { onFocus(); engine.reload() }
                else if (page.url == "about:blank" && !page.rendersBlankDocument) Box(Modifier.fillMaxSize()) {
                    WebUrlDropTarget(Modifier.matchParentSize()) { url -> onFocus(); engine.loadUrl(url) }
                    val tabs = state.visibleTabs
                    val shortcuts = remember(tabs, tab.id, compactWindow) { newTabShortcuts(tabs, tab.id, compactWindow) }
                    key(tab.id) {
                        NewTabPage({ onFocus(); onNewTabSearch(newTabSearchBounds) }, compactWindow, state.settings, onCustomizeNewTab,
                            shortcuts = shortcuts, onResumeTab = onResumeTab, requestInitialFocus = focused,
                            onSearchBounds = { newTabSearchBounds = it })
                    }
                }
                else if (page.readerArticle != null) ReaderPane(page.readerArticle!!, engine)
                else BrowserSurface(engine, Modifier.fillMaxSize().clip(if (compactWindow) RectangleShape else NagiShapes.Bottom), onFocus, trackpadPreview)
                if (bottomSearchBar && page.isLoading) LinearProgressIndicator(progress = { page.progress / 100f },
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(2.dp))
                if (page.readerLoading) CircularProgressIndicator(Modifier.align(Alignment.Center))
                // Chrome-style: a compact card floating over the page's top end; the page does not move.
                if (findOpen) FindInPageBar(findQuery, { findQuery = it },
                    if (findQuery.isEmpty()) "" else "${if (page.findMatches == 0) 0 else page.activeFindMatch + 1}/${page.findMatches}",
                    onPrevious = { engine.findNext(false) }, onNext = { engine.findNext(true) }, onClose = onCloseFind,
                    modifier = Modifier.align(Alignment.TopEnd))
              }
            }
            if (bottomSearchBar && !WindowInsets.isImeVisible) AddressBar()
        }
    }
}

@Composable
private fun FindInPageBar(query: String, onQuery: (String) -> Unit, count: String,
    onPrevious: () -> Unit, onNext: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val strings = rememberNagiStrings()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val colors = MaterialTheme.colorScheme
    Surface(shape = NagiShapes.Rounded, color = colors.surfaceBright, contentColor = colors.onSurface, shadowElevation = 6.dp,
        modifier = modifier.padding(8.dp).widthIn(max = 420.dp).fillMaxWidth().testTag("find-in-page")) {
        Row(Modifier.height(48.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            // An unadorned field, like Chrome's: no filled container or underline inside the card.
            BasicTextField(query, onQuery, singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onNext() }),
                modifier = Modifier.weight(1f).focusRequester(focus),
                decorationBox = { field ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text(strings(R.string.ui_find_in_page), style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        field()
                    }
                })
            if (count.isNotEmpty()) Text(count, Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant)
            VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp), color = colors.outlineVariant)
            FindButton(NagiIcons.ChevronUp, strings(R.string.ui_previous_match), onPrevious)
            FindButton(NagiIcons.ChevronDown, strings(R.string.ui_next_match), onNext)
            FindButton(NagiIcons.X, strings(R.string.ui_close_find), onClose)
        }
    }
}

@Composable
private fun FindButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) { Icon(icon, label, Modifier.size(20.dp)) }
}
