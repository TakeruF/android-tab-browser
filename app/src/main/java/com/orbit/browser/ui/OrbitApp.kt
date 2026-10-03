package com.orbit.browser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.orbit.browser.*
import com.orbit.browser.browser.engine.*
import com.orbit.browser.browser.search.*
import com.orbit.browser.browser.tabs.BrowserSessionController
import com.orbit.browser.domain.model.*
import com.orbit.browser.ui.browser.*
import com.orbit.browser.ui.commandbar.CommandBar
import com.orbit.browser.ui.library.LibraryScreen
import com.orbit.browser.ui.settings.SettingsScreen
import com.orbit.browser.ui.sidebar.Sidebar
import com.orbit.browser.ui.theme.OrbitTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun OrbitApp(activity: MainActivity, host: NativeBrowserHost, container: AppContainer, incomingUrls: Flow<String>) {
    val vm: BrowserViewModel = viewModel(factory = BrowserViewModel.Factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val currentSettings by rememberUpdatedState(state.settings)
    val sessions = remember(host) { BrowserSessionController(container) { _, desktop ->
        WebViewBrowserEngine(activity, host, host, { currentSettings.openLinksInNewTab }, desktop)
    } }
    val pages by sessions.pages.collectAsStateWithLifecycle()
    DisposableEffect(sessions) { onDispose { sessions.dispose() } }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "browser"
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var omniboxOpen by rememberSaveable { mutableStateOf(false) }
    var omniboxInitial by rememberSaveable { mutableStateOf("") }
    var rightTabId by rememberSaveable { mutableStateOf<String?>(null) }
    var previousSpaceId by rememberSaveable { mutableStateOf<String?>(null) }
    var rightFocused by rememberSaveable { mutableStateOf(false) }
    var splitRatio by rememberSaveable { mutableFloatStateOf(0.5f) }
    var showFind by rememberSaveable { mutableStateOf(false) }
    val right = state.visibleTabs.firstOrNull { it.id == rightTabId && it.id != state.activeTab?.id }
    val focusedTab = if (rightFocused && right != null) right else state.activeTab
    val focusedEngine = focusedTab?.let { sessions.pool.peek(it.id) }
    val fullscreen by host.fullscreen.collectAsStateWithLifecycle()
    fun navigate(destination: String) {
        if (destination == "browser") nav.popBackStack("browser", inclusive = false)
        else nav.navigate(destination) { launchSingleTop = true; popUpTo("browser") }
    }
    fun openOmnibox(preselectUrl: Boolean = true) {
        omniboxInitial = if (preselectUrl) (focusedEngine?.state?.value?.url ?: focusedTab?.url)
            ?.takeUnless { it == "about:blank" }.orEmpty() else ""
        omniboxOpen = true
    }
    fun openUrl(url: String) {
        navigate("browser")
        val tab = focusedTab
        if (tab != null) sessions.navigate(tab, url, state.settings.desktopDefault) else vm.newTab(url)
    }
    fun split() {
        val candidate = state.visibleTabs.firstOrNull { it.id != state.activeTab?.id }
        if (candidate != null) rightTabId = candidate.id
        else scope.launch { rightTabId = vm.createTab(select = false) }
        rightFocused = false; navigate("browser")
    }
    fun command(value: BrowserCommand) { when (value) {
        BrowserCommand.NEW_TAB -> { vm.newTab(); rightFocused = false; navigate("browser") }
        BrowserCommand.SPLIT -> split()
        BrowserCommand.CLOSE_SPLIT -> { rightTabId = null; rightFocused = false }
        BrowserCommand.RESTORE_TAB -> vm.restoreClosed()
        BrowserCommand.TOGGLE_SIDEBAR -> vm.updateSettings { it.copy(sidebarCollapsed = !it.sidebarCollapsed) }
        BrowserCommand.FIND -> { showFind = true; navigate("browser") }
        BrowserCommand.SETTINGS -> navigate("settings")
        BrowserCommand.HISTORY -> navigate("history")
        BrowserCommand.BOOKMARKS -> navigate("bookmarks")
        BrowserCommand.DESKTOP -> focusedEngine?.let { it.setDesktopMode(!it.state.value.desktopMode) }
    } }
    val shortcutHandler by rememberUpdatedState<(Shortcut) -> Boolean>({ shortcut ->
        val escapeHandled = fullscreen != null || omniboxOpen || showFind
        when (shortcut) {
            Shortcut.OMNIBOX -> openOmnibox()
            Shortcut.NEW_TAB -> command(BrowserCommand.NEW_TAB)
            Shortcut.CLOSE_TAB -> focusedTab?.let { if (rightFocused) { rightTabId = null; rightFocused = false }; vm.closeTab(it.id) }
            Shortcut.RESTORE_TAB -> command(BrowserCommand.RESTORE_TAB)
            Shortcut.NEXT_TAB -> { rightFocused = false; vm.cycleTab(true) }
            Shortcut.PREVIOUS_TAB -> { rightFocused = false; vm.cycleTab(false) }
            Shortcut.RELOAD -> focusedEngine?.reload()
            Shortcut.BACK -> focusedEngine?.goBack()
            Shortcut.FORWARD -> focusedEngine?.goForward()
            Shortcut.FIND -> { showFind = true; navigate("browser") }
            Shortcut.ESCAPE -> when {
                fullscreen != null -> host.hideFullscreen()
                omniboxOpen -> omniboxOpen = false
                showFind -> showFind = false
                else -> Unit
            }
        }
        shortcut != Shortcut.ESCAPE || escapeHandled
    })
    DisposableEffect(activity) {
        activity.shortcutHandler = { shortcutHandler(it) }
        onDispose { activity.shortcutHandler = null }
    }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(incomingUrls) { incomingUrls.collect { url ->
        vm.state.first { it.ready }
        vm.newTab(url); navigate("browser")
    } }
    LaunchedEffect(state.currentSpace?.id) {
        state.currentSpace?.id?.let { id ->
            if (previousSpaceId != null && previousSpaceId != id) { rightTabId = null; rightFocused = false }
            previousSpaceId = id
        }
    }
    LaunchedEffect(state.workspace.tabs) {
        sessions.pool.retainTabIds(state.workspace.tabs.filter { it.closedAt == null && it.archivedAt == null }.map { it.id }.toSet())
        if (rightTabId != null && right == null) rightFocused = false
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val visibleIds by rememberUpdatedState(if (route == "browser") setOfNotNull(state.activeTab?.id, right?.id) else emptySet())
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> when (event) {
            Lifecycle.Event.ON_STOP -> sessions.pool.setVisible(emptySet())
            Lifecycle.Event.ON_START -> { sessions.pool.setVisible(visibleIds); container.scope.launch { container.regionalSearch.refresh() } }
            else -> Unit
        } }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    BackHandler(enabled = fullscreen != null) { host.hideFullscreen() }
    BackHandler(enabled = fullscreen == null && route == "browser" && pages[focusedTab?.id]?.canGoBack == true) { focusedEngine?.goBack() }
    OrbitTheme(state.settings.theme) {
        Surface(color = androidx.compose.ui.graphics.Color(state.currentSpace?.color ?: 0xFF426B5A).copy(alpha = 0.1f).compositeOver(MaterialTheme.colorScheme.background),
            contentColor = MaterialTheme.colorScheme.onBackground, modifier = Modifier.fillMaxSize()) {
            if (!state.ready) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.startupError != null) Text("Workspace could not be opened: ${state.startupError}") else CircularProgressIndicator()
            } else Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                val density = LocalDensity.current
                var resizingWidth by remember { mutableStateOf<Float?>(null) }
                val width = if (state.settings.sidebarCollapsed) 72.dp else (resizingWidth ?: state.settings.sidebarWidth).dp
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.width(width).fillMaxHeight()) {
                        Sidebar(state, vm, pages.filterValues { it.isLoading }.keys, onNavigate = ::navigate, onOmnibox = { openOmnibox(false) },
                            onOpenUrl = { vm.newTab(it); rightFocused = false; navigate("browser") }, onSplitTab = { id ->
                                if (id == state.activeTab?.id) split() else { rightTabId = id; rightFocused = true; navigate("browser") }
                            })
                    }
                    val widthValue by rememberUpdatedState(resizingWidth ?: state.settings.sidebarWidth)
                    val collapsed by rememberUpdatedState(state.settings.sidebarCollapsed)
                    Box(Modifier.width(8.dp).fillMaxHeight().pointerInput(density) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(onDragStart = { totalDrag = 0f }, onDragEnd = {
                            if (collapsed && totalDrag > 30f) vm.updateSettings { it.copy(sidebarCollapsed = false) }
                            else if (!collapsed && totalDrag < -120f) vm.updateSettings { it.copy(sidebarCollapsed = true) }
                            else resizingWidth?.let { value -> vm.updateSettings { it.copy(sidebarWidth = value) } }
                            resizingWidth = null
                        }, onDragCancel = { resizingWidth = null }, onHorizontalDrag = { change, delta ->
                            change.consume(); totalDrag += delta
                            if (!collapsed) resizingWidth = (widthValue + delta / density.density).coerceIn(220f, 380f)
                        })
                    })
                    Surface(shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).fillMaxHeight().padding(top = 8.dp, end = 8.dp, bottom = 8.dp),
                        color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                        NavHost(navController = nav, startDestination = "browser") {
                            composable("browser") { BrowserScreen(state, vm, sessions, rightTabId, rightFocused,
                                onFocusRight = { rightFocused = it }, splitRatio, onSplitRatio = { splitRatio = it },
                                showFind, onFind = { showFind = it }, onOmnibox = { openOmnibox() }, onOpenUrl = ::openUrl,
                                onSplit = ::split, onCloseSplit = { rightTabId = null; rightFocused = false },
                                onSwap = { right?.let { val old = state.activeTab?.id; vm.selectTab(it.id); rightTabId = old; rightFocused = !rightFocused } },
                                onSelectRight = { rightTabId = it; rightFocused = true }) }
                            composable("settings") { SettingsScreen(state, vm) { navigate("browser") } }
                            composable("history") { LibraryScreen(true, state, vm, { navigate("browser") }, { vm.newTab(it); navigate("browser") }) }
                            composable("bookmarks") { LibraryScreen(false, state, vm, { navigate("browser") }, { vm.newTab(it); navigate("browser") }) }
                        }
                    }
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
                if (omniboxOpen) CommandBar(omniboxInitial, state.workspace, state.settings, onDismiss = { omniboxOpen = false }, onExecute = { action ->
                    omniboxOpen = false
                    when (action) {
                        is SuggestionAction.Navigate -> openUrl(action.url)
                        is SuggestionAction.SelectTab -> { rightFocused = false; vm.selectTab(action.id); navigate("browser") }
                        is SuggestionAction.SelectSpace -> { vm.selectSpace(action.id); navigate("browser") }
                        is SuggestionAction.Command -> command(action.command)
                    }
                })
            }
            fullscreen?.let { view -> Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
                NativeSurface(view, Modifier.fillMaxSize())
                TextButton(onClick = host::hideFullscreen, modifier = Modifier.align(Alignment.TopEnd).padding(24.dp)) { Text("Exit fullscreen", color = androidx.compose.ui.graphics.Color.White) }
            } }
        }
    }
}
