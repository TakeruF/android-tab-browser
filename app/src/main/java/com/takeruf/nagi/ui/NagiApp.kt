package com.takeruf.nagi.ui

import com.takeruf.nagi.ui.theme.NagiShapes
import com.takeruf.nagi.R
import com.takeruf.nagi.ui.localization.rememberNagiStrings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.takeruf.nagi.*
import com.takeruf.nagi.browser.engine.*
import com.takeruf.nagi.browser.search.*
import com.takeruf.nagi.browser.tabs.BrowserSessionController
import com.takeruf.nagi.domain.model.*
import com.takeruf.nagi.ui.browser.*
import com.takeruf.nagi.ui.commandbar.CommandBar
import com.takeruf.nagi.ui.library.LibraryScreen
import com.takeruf.nagi.ui.settings.SettingsScreen
import com.takeruf.nagi.ui.sidebar.*
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import com.takeruf.nagi.ui.theme.NagiTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun NagiApp(activity: MainActivity, host: NativeBrowserHost, container: AppContainer, incomingUrls: Flow<String>) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        NagiAppContent(activity, host, container, incomingUrls, maxWidth.value)
    }
}

@Composable
private fun NagiAppContent(activity: MainActivity, host: NativeBrowserHost, container: AppContainer,
    incomingUrls: Flow<String>, windowWidthDp: Float) {
    val strings = rememberNagiStrings()
    val vm: BrowserViewModel = viewModel(factory = BrowserViewModel.Factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    var savedSidebarAdaptation by rememberSaveable(stateSaver = AdaptiveSidebarState.Saver) {
        mutableStateOf(AdaptiveSidebarState())
    }
    val sidebarAdaptation = savedSidebarAdaptation.forWidth(windowWidthDp)
    SideEffect { savedSidebarAdaptation = sidebarAdaptation }
    val sidebarCollapsed = sidebarAdaptation.isCollapsed(state.settings.sidebarCollapsed)
    fun setSidebarCollapsed(collapsed: Boolean) {
        if (sidebarAdaptation.compact) {
            savedSidebarAdaptation = sidebarAdaptation.copy(compactCollapsed = collapsed)
        } else vm.updateSettings { it.copy(sidebarCollapsed = collapsed) }
    }
    val currentStrings by rememberUpdatedState(strings)
    val currentSettings by rememberUpdatedState(state.settings)
    SideEffect { host.blockExternalApps = { currentSettings.blockExternalApps } }
    val sessions = remember(host) { BrowserSessionController(container) { _, desktop ->
        WebViewBrowserEngine(activity, host, host, { currentSettings.openLinksInNewTab }, desktop,
            nativePageDrag = { currentSettings.nativePageDrag })
    } }
    val blockedExternalApp by host.blockedExternalApps.collectAsStateWithLifecycle()
    val pages by sessions.pages.collectAsStateWithLifecycle()
    DisposableEffect(sessions) { onDispose { sessions.dispose() } }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "browser"
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val updateState by container.updates.state.collectAsStateWithLifecycle()
    var updateDialog by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(container.updates) { if (com.takeruf.nagi.BuildConfig.APK_UPDATES_ENABLED && container.updates.state.value.status == com.takeruf.nagi.updates.UpdateStatus.IDLE) container.updates.check() }
    var omniboxOpen by rememberSaveable { mutableStateOf(false) }
    var omniboxInitial by rememberSaveable { mutableStateOf("") }
    var pendingNewTabId by remember { mutableStateOf<String?>(null) }
    val savedPair = state.visibleTabs.firstOrNull { tab ->
        tab.splitRightTabId != null && state.visibleTabs.any { it.id == tab.splitRightTabId } &&
            state.activeTab?.id in listOf(tab.id, tab.splitRightTabId)
    }
    val leftTabId = savedPair?.id
    val rightTabId = savedPair?.splitRightTabId
    var previousSpaceId by rememberSaveable { mutableStateOf<String?>(null) }
    var rightFocused by rememberSaveable { mutableStateOf(false) }
    val sidebarDrag = remember { SidebarDragState() }
    var splitRatio by rememberSaveable { mutableFloatStateOf(0.5f) }
    var showFind by rememberSaveable { mutableStateOf(false) }
    // A split belongs to its two tabs, independently of the selected standalone tab.
    val splitLeft = state.visibleTabs.firstOrNull { it.id == leftTabId }
    val splitRight = state.visibleTabs.firstOrNull { it.id == rightTabId && it.id != leftTabId }
    val splitVisible = splitLeft != null && splitRight != null &&
        (state.activeTab?.id == leftTabId || state.activeTab?.id == rightTabId)
    val left = if (splitVisible) splitLeft else state.activeTab
    val right = splitRight.takeIf { splitVisible }
    val focusedTab = if (rightFocused && right != null) right else left
    val focusedEngine = focusedTab?.let { sessions.pool.peek(it.id) }
    SideEffect {
        val protectedIds = setOfNotNull(left?.id, right?.id)
        container.tabs.protectFromArchive(protectedIds)
        sessions.pool.protect(protectedIds)
    }
    DisposableEffect(container) { onDispose { container.tabs.protectFromArchive(emptySet()) } }
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
    fun newTab() {
        scope.launch { pendingNewTabId = vm.createTab() }
    }
    // Wait for the selected tab to reach the UI before accepting search input.
    LaunchedEffect(pendingNewTabId, state.activeTab?.id) {
        if (pendingNewTabId != null && state.activeTab?.id == pendingNewTabId) {
            pendingNewTabId = null
            rightFocused = false
            navigate("browser")
            openOmnibox(false)
        }
    }
    fun openUrl(url: String) {
        navigate("browser")
        val tab = focusedTab
        if (tab != null) sessions.navigate(tab, url, state.settings.desktopDefault) else vm.newTab(url)
    }
    val canCloseTabOnBack = focusedTab != null && !focusedTab.isPinned && state.visibleTabs.size > 1
    fun back() {
        if (focusedEngine?.canGoBack() == true) focusedEngine.goBack()
        else if (canCloseTabOnBack) focusedTab?.let { tab ->
            vm.closeTabOnBack(tab.id)
            rightFocused = false
            showFind = false
        }
    }
    fun split() {
        val leftId = state.activeTab?.id ?: return
        val candidate = state.visibleTabs.firstOrNull { tab ->
            tab.id != leftId && tab.splitRightTabId == null && state.visibleTabs.none { it.splitRightTabId == tab.id }
        }
        if (candidate != null) vm.pairTabs(leftId, candidate.id)
        else scope.launch { vm.createTab(select = false)?.let { vm.pairTabs(leftId, it) } }
        rightFocused = false; navigate("browser")
    }
    fun closeSplit() { leftTabId?.let(vm::detachSplit); rightFocused = false }
    fun command(value: BrowserCommand) { when (value) {
        BrowserCommand.NEW_TAB -> newTab()
        BrowserCommand.SPLIT -> split()
        BrowserCommand.CLOSE_SPLIT -> closeSplit()
        BrowserCommand.RESTORE_TAB -> vm.restoreClosed()
        BrowserCommand.TOGGLE_SIDEBAR -> setSidebarCollapsed(!sidebarCollapsed)
        BrowserCommand.FIND -> { showFind = true; navigate("browser") }
        BrowserCommand.SETTINGS -> navigate("settings")
        BrowserCommand.HISTORY -> navigate("history")
        BrowserCommand.DESKTOP -> focusedEngine?.let { it.setDesktopMode(!it.state.value.desktopMode) }
    } }
    val shortcutHandler by rememberUpdatedState<(Shortcut) -> Boolean>({ shortcut ->
        val escapeHandled = fullscreen != null || omniboxOpen || showFind
        when (shortcut) {
            Shortcut.OMNIBOX -> openOmnibox()
            Shortcut.NEW_TAB -> command(BrowserCommand.NEW_TAB)
            Shortcut.CLOSE_TAB -> focusedTab?.let { if (right != null && rightFocused) rightFocused = false; vm.closeTab(it.id) }
            Shortcut.RESTORE_TAB -> command(BrowserCommand.RESTORE_TAB)
            Shortcut.NEXT_TAB -> { rightFocused = vm.cycleTab(true) == rightTabId }
            Shortcut.PREVIOUS_TAB -> { rightFocused = vm.cycleTab(false) == rightTabId }
            Shortcut.TAB_1, Shortcut.TAB_2, Shortcut.TAB_3, Shortcut.TAB_4,
            Shortcut.TAB_5, Shortcut.TAB_6, Shortcut.TAB_7, Shortcut.TAB_8, Shortcut.TAB_9 -> {
                if (vm.selectNumberedTab(requireNotNull(shortcut.tabNumber))) {
                    rightFocused = (state.numberedTabTarget(requireNotNull(shortcut.tabNumber)) as? NumberedTabTarget.Tab)?.id == rightTabId && rightTabId != null
                    omniboxOpen = false
                    showFind = false
                    fullscreen?.let { host.hideFullscreen() }
                    navigate("browser")
                }
            }
            Shortcut.RELOAD -> focusedEngine?.reload()
            Shortcut.BACK -> back()
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
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(currentStrings.translate(it)) } }
    LaunchedEffect(incomingUrls) { incomingUrls.collect { url ->
        vm.state.first { it.ready }
        vm.newTab(url); navigate("browser")
    } }
    LaunchedEffect(state.currentSpace?.id) {
        state.currentSpace?.id?.let { id ->
            if (previousSpaceId != null && previousSpaceId != id) { rightFocused = false }
            previousSpaceId = id
        }
    }
    LaunchedEffect(state.activeTab?.id, rightTabId) { rightFocused = rightTabId != null && state.activeTab?.id == rightTabId }
    LaunchedEffect(state.workspace.tabs) {
        sessions.pool.retainTabIds(state.workspace.tabs.filter { it.closedAt == null && it.archivedAt == null }.map { it.id }.toSet())
        if (rightTabId != null && right == null) rightFocused = false
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val visibleIds by rememberUpdatedState(if (route == "browser") setOfNotNull(left?.id, right?.id) else emptySet())
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
    BackHandler(enabled = fullscreen == null && route == "browser" && (pages[focusedTab?.id]?.canGoBack == true || canCloseTabOnBack)) { back() }
    NagiTheme(state.settings.theme, state.currentSpace?.color ?: state.settings.themeColor) {
        Surface(color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground, modifier = Modifier.fillMaxSize()) {
            if (!state.ready) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.startupError != null) Text(strings(R.string.ui_workspace_could_not_be_opened_1_s, strings.translate(state.startupError.orEmpty()))) else CircularProgressIndicator()
            } else Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                val density = LocalDensity.current
                var resizingWidth by remember { mutableStateOf<Float?>(null) }
                var resizingCollapsed by remember { mutableStateOf<Boolean?>(null) }
                val previewCollapsed = resizingCollapsed ?: sidebarCollapsed
                val previewState = state.copy(settings = state.settings.copy(sidebarCollapsed = previewCollapsed))
                val width = if (previewCollapsed) 72.dp else (resizingWidth ?: state.settings.sidebarWidth).dp
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.width(width).fillMaxHeight().padding(vertical = 8.dp).testTag("sidebar")) {
                        Sidebar(previewState, vm, pages.filterValues { it.isLoading }.keys,
                            suspendedIds = pages.filterValues { it.isSuspended }.keys,
                            pageUrl = pages[focusedTab?.id]?.url ?: focusedTab?.url.orEmpty(),
                            pageTitle = pages[focusedTab?.id]?.title ?: focusedTab?.title.orEmpty(),
                            onNavigate = ::navigate, onNewTab = ::newTab, onOmnibox = { openOmnibox(false) },
                            onOpenUrl = { vm.newTab(it); rightFocused = false; navigate("browser") }, onSplitTab = { id ->
                                if (id == state.activeTab?.id) split() else { state.activeTab?.id?.let { vm.pairTabs(it, id) }; rightFocused = true; navigate("browser") }
                            }, splitLeftTabId = splitLeft?.id, splitRightTabId = splitRight?.id, splitRightFocused = rightFocused,
                            onDetachSplit = { id ->
                                vm.detachSplit(id)
                                rightFocused = false
                                vm.selectTab(id)
                                navigate("browser")
                            }, onFocusSplit = { rightFocused = it; (if (it) splitRight else splitLeft)?.let { tab -> vm.selectTab(tab.id) } }, drag = sidebarDrag, onSplitDrop = { id, toRight ->
                                val targetId = sidebarDrag.destination?.id
                                val targetLeft = state.visibleTabs.firstOrNull { it.id == targetId && it.splitRightTabId != null || it.splitRightTabId == targetId && targetId != null }
                                val dropLeft = targetLeft ?: left
                                val dropRight = targetLeft?.splitRightTabId?.let { rightId -> state.visibleTabs.firstOrNull { it.id == rightId } } ?: right
                                val leftId = dropLeft?.id
                                if (leftId != null && state.visibleTabs.any { it.id == id }) {
                                    val pairLeft = if (toRight) leftId.takeUnless { it == id } ?: dropRight?.id else id
                                    val pairRight = if (toRight) id else dropRight?.id?.takeUnless { it == id } ?: leftId
                                    if (pairLeft != null && pairLeft != pairRight) vm.pairTabs(pairLeft, pairRight)
                                    rightFocused = toRight
                                    vm.selectTab(id)
                                    navigate("browser")
                                }
                            }, onSplitPair = { targetId, draggedId ->
                                if (targetId != draggedId && state.visibleTabs.any { it.id == targetId } &&
                                    state.visibleTabs.any { it.id == draggedId }) {
                                    vm.pairTabs(targetId, draggedId)
                                    rightFocused = true
                                    vm.selectTab(draggedId)
                                    navigate("browser")
                                }
                            }, onToggleSidebar = { setSidebarCollapsed(!sidebarCollapsed) })
                    }
                    val resizeSettings by rememberUpdatedState(state.settings.copy(sidebarCollapsed = sidebarCollapsed))
                    val resizeCollapsed by rememberUpdatedState<(Boolean) -> Unit>(::setSidebarCollapsed)
                    var resizeOriginX by remember { mutableFloatStateOf(0f) }
                    val hoverSource = remember { MutableInteractionSource() }
                    val edgeHovered by hoverSource.collectIsHoveredAsState()
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val resizeCursor = remember(context) { PointerIcon(android.view.PointerIcon.getSystemIcon(context,
                        android.view.PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW)) }
                    Box(Modifier.width(8.dp).fillMaxHeight().testTag("sidebar-resize-handle")
                        .onGloballyPositioned { resizeOriginX = it.positionInRoot().x }
                        .pointerHoverIcon(resizeCursor).hoverable(hoverSource)
                        .pointerInput(density) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                if (down.type == PointerType.Mouse && !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
                                val downX = resizeOriginX + down.position.x
                                val startWidth = resizeSettings.sidebarWidth
                                val startCollapsed = resizeSettings.sidebarCollapsed
                                val slop = if (down.type == PointerType.Mouse) 1f else viewConfiguration.touchSlop
                                var dragging = false
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (change.isConsumed) break
                                        val delta = resizeOriginX + change.position.x - downX
                                        if (kotlin.math.abs(delta) > slop) dragging = true
                                        if (dragging) {
                                            val raw = (if (startCollapsed) 72f else startWidth) + delta / density.density
                                            resizingCollapsed = raw <= (if (startCollapsed) 102f else 220f)
                                            resizingWidth = raw.coerceIn(220f, 380f)
                                        }
                                        val released = change.changedToUp()
                                        change.consume()
                                        if (!change.pressed) {
                                            if (released && dragging) {
                                                val collapse = resizingCollapsed ?: startCollapsed
                                                val value = resizingWidth ?: startWidth
                                                resizeCollapsed(collapse)
                                                vm.updateSettings { it.copy(sidebarWidth = if (collapse) startWidth else value) }
                                            }
                                            break
                                        }
                                    }
                                } finally { resizingWidth = null; resizingCollapsed = null }
                            }
                        }, contentAlignment = Alignment.Center) {
                        if (edgeHovered || resizingWidth != null) Box(Modifier.width(2.dp).fillMaxHeight()
                            .padding(vertical = 16.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
                    }
                    Surface(shape = NagiShapes.Rounded, modifier = Modifier.weight(1f).fillMaxHeight().padding(top = 8.dp, end = 8.dp, bottom = 8.dp),
                        color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                        Box(Modifier.fillMaxSize()) {
                            NavHost(navController = nav, startDestination = "browser") {
                                composable("browser") { BrowserScreen(state, vm, sessions, right?.id, rightFocused,
                                    onFocusRight = { rightFocused = it }, splitRatio, onSplitRatio = { splitRatio = it },
                                    showFind, onFind = { showFind = it }, onOmnibox = { openOmnibox() }, onOpenUrl = ::openUrl,
                                    onSplit = ::split, onCloseSplit = ::closeSplit,
                                    onSwap = { right?.let { other -> left?.let { vm.pairTabs(other.id, it.id); vm.selectTab(other.id) } } },
                                    leftTabId = left?.id) }
                                composable("settings") { SettingsScreen(state, vm,
                                    onSuspendTabs = sessions::suspendBackground,
                                    onClearSiteData = { scope.launch {
                                        sessions.reset()
                                        BrowserDataCleaner.clear(activity)
                                        sessions.reset()
                                        snackbar.showSnackbar(strings(R.string.ui_site_data_cleared))
                                    } },
                                    updateSection = { com.takeruf.nagi.updates.AppUpdateSection(container.updates, activity.updateInstaller::install) }) { navigate("browser") } }
                                composable("history") { LibraryScreen(state, vm, { navigate("browser") }, { vm.newTab(it); navigate("browser") }) }
                            }
                            if (route == "browser" && state.activeTab != null) {
                                BoxWithConstraints(Modifier.fillMaxSize()) {
                                    val availableWidth = (maxWidth - 24.dp).coerceAtLeast(1.dp)
                                    val minimumRatio = maxOf(0.25f, (180.dp / availableWidth).coerceAtMost(0.5f))
                                    val ratio = splitRatio.coerceIn(minimumRatio, 1f - minimumRatio)
                                    Row(Modifier.fillMaxSize()) {
                                        listOf(SidebarSection.SPLIT_LEFT, SidebarSection.SPLIT_RIGHT).forEach { side ->
                                            val highlighted = sidebarDrag.item?.favorite == false && sidebarDrag.destination?.section == side
                                            if (right != null && side == SidebarSection.SPLIT_RIGHT) Spacer(Modifier.width(24.dp))
                                            Box(Modifier.weight(if (right == null) 1f else if (side == SidebarSection.SPLIT_LEFT) ratio else 1f - ratio).fillMaxHeight()
                                                .testTag(if (side == SidebarSection.SPLIT_LEFT) "split-drop-left" else "split-drop-right")
                                                .sidebarTarget(sidebarDrag, side.name, SidebarDestination(side))) {
                                                if (highlighted) Box(Modifier.fillMaxSize().padding(12.dp)
                                                    .clip(NagiShapes.Rounded)
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                                                    .border(2.dp, MaterialTheme.colorScheme.primary, NagiShapes.Rounded))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (route == "browser") blockedExternalApp?.let { request ->
                    ExternalAppNotice(request,
                        onAllowOnce = { host.allowBlockedExternalApp(request) },
                        onAllowAlways = {
                            scope.launch {
                                if (host.blockedExternalApps.value !== request) return@launch
                                container.settings.update { it.copy(blockExternalApps = false) }
                                host.allowBlockedExternalApp(request)
                            }
                        },
                        onDismiss = { host.dismissBlockedExternalApp(request) },
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 16.dp)
                            .widthIn(max = (windowWidthDp - width.value - 32f).coerceIn(200f, 360f).dp))
                }
                if (sidebarDrag.item != null) SidebarDragPreview(sidebarDrag)
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
                if (com.takeruf.nagi.BuildConfig.APK_UPDATES_ENABLED && updateState.release != null && updateState.status in setOf(com.takeruf.nagi.updates.UpdateStatus.AVAILABLE, com.takeruf.nagi.updates.UpdateStatus.READY)) {
                    TextButton(onClick = { updateDialog = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                        Text(strings(R.string.ui_update_banner, updateState.release!!.versionName))
                    }
                }
                if (com.takeruf.nagi.BuildConfig.APK_UPDATES_ENABLED && updateDialog) AlertDialog(onDismissRequest = { updateDialog = false },
                    title = { Text(strings(R.string.ui_app_updates)) },
                    text = { com.takeruf.nagi.updates.AppUpdateSection(container.updates, activity.updateInstaller::install) },
                    confirmButton = { TextButton(onClick = { updateDialog = false }) { Text(strings(R.string.ui_cancel)) } })
                if (omniboxOpen) CommandBar(omniboxInitial, state.workspace, state.settings, onDismiss = { omniboxOpen = false }, onExecute = { action ->
                    omniboxOpen = false
                    when (action) {
                        is SuggestionAction.Navigate -> openUrl(action.url)
                        is SuggestionAction.SelectTab -> { rightFocused = action.id == rightTabId && rightTabId != null; vm.selectTab(action.id); navigate("browser") }
                        is SuggestionAction.SelectSpace -> { vm.selectSpace(action.id); navigate("browser") }
                        is SuggestionAction.Command -> command(action.command)
                    }
                })
            }
            fullscreen?.let { view -> Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
                NativeSurface(view, Modifier.fillMaxSize())
                TextButton(shape = NagiShapes.Rounded, onClick = host::hideFullscreen, modifier = Modifier.align(Alignment.TopEnd).padding(24.dp)) { Text(strings(R.string.ui_exit_fullscreen), color = androidx.compose.ui.graphics.Color.White) }
            } }
        }
    }
}
