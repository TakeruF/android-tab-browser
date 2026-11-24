package com.takeruf.nagi.ui.sidebar

import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource

import com.takeruf.nagi.ui.theme.NagiShapes
import com.takeruf.nagi.R
import com.takeruf.nagi.ui.localization.rememberNagiStrings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import com.takeruf.nagi.ui.components.NagiIcons
import com.takeruf.nagi.ui.components.ThemeColorPicker
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.takeruf.nagi.ui.theme.themeColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.takeruf.nagi.domain.model.*
import com.takeruf.nagi.ui.browser.*
import com.takeruf.nagi.ui.components.*
import com.takeruf.nagi.ui.theme.readableSpaceColor
import com.takeruf.nagi.ui.theme.NagiSystemBars
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sidebar(state: BrowserUiState, vm: BrowserViewModel, loadingIds: Set<String>, onNavigate: (String) -> Unit,
    onNewTab: () -> Unit, onOmnibox: () -> Unit, onOpenUrl: (String) -> Unit, onSplitTab: (String) -> Unit, drag: SidebarDragState, onSplitDrop: (String, Boolean) -> Unit, onSplitPair: (String, String) -> Unit = { _, _ -> },
    onFavoritePaneDrop: (String, SidebarDestination) -> Unit = { _, _ -> },
    splitLeftTabId: String? = null, splitRightTabId: String? = null, splitRightFocused: Boolean = false, onFocusSplit: (Boolean) -> Unit = {}, onDetachSplit: (String) -> Unit = {},
    suspendedIds: Set<String> = emptySet(),
    pageUrl: String = state.activeTab?.url.orEmpty(), pageTitle: String = state.activeTab?.title.orEmpty(),
    onToggleSidebar: () -> Unit = { vm.updateSettings { it.copy(sidebarCollapsed = !it.sidebarCollapsed) } },
    overlay: Boolean = false, backgroundExtension: Dp = 0.dp) {
    val strings = rememberNagiStrings()
    val dimensions = LocalNagiUiDimensions.current
    val collapsed = state.settings.sidebarCollapsed
    LaunchedEffect(dimensions.compact) {
        // A density change invalidates the pointer's cached source/target rectangles.
        drag.cancel(); drag.finishFavoriteLanding()
    }
    var spacesMenu by remember { mutableStateOf(false) }
    var editingSpace by remember { mutableStateOf<Space?>(null) }
    var editingFavoriteId by rememberSaveable { mutableStateOf<String?>(null) }
    var createSpace by remember { mutableStateOf(false) }
    var deleteSpace by remember { mutableStateOf<Space?>(null) }
    var mouseInput by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val spacesEnabled = state.settings.spacesEnabled
    var spaceSwitcherExpanded by remember(collapsed, spacesEnabled) { mutableStateOf(false) }
    val spaces = if (spacesEnabled) state.workspace.spaces else listOfNotNull(state.currentSpace)
    val splitLeft = state.visibleTabs.firstOrNull { it.id == splitLeftTabId }
    val splitRight = state.visibleTabs.firstOrNull { it.id == splitRightTabId && it.id != splitLeft?.id }
    fun selectTab(tab: BrowserTab) {
        if (!overlay && splitRight != null && (tab.id == splitLeft?.id || tab.id == splitRight.id)) onFocusSplit(tab.id == splitRight.id)
        vm.selectTab(tab.id)
        onNavigate("browser")
    }
    val selectedPage = spaces.indexOfFirst { it.id == state.currentSpace?.id }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = selectedPage) { spaces.size }
    val scope = rememberCoroutineScope()
    var settling by remember { mutableStateOf<Job?>(null) }
    var gesturePage by remember { mutableIntStateOf(selectedPage) }
    // Compose applies the system animator duration scale to this settling animation.
    val settleSpec = remember { tween<Float>(250, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)) }
    fun settle(page: Int) {
        settling?.cancel()
        settling = scope.launch {
            // The sidebar can enter composition inside BoxWithConstraints' measure pass
            // (for example after rotation). Pager scrolling forces a remeasure, so start
            // it on the next frame, after that enclosing layout has finished.
            withFrameNanos { }
            pager.animateScrollToPage(page, animationSpec = settleSpec)
        }
    }
    LaunchedEffect(state.currentSpace?.id, spaces.map { it.id }) {
        if (spaces.isNotEmpty()) settle(selectedPage)
    }
    @Composable
    fun SpacePages(modifier: Modifier, content: @Composable (BrowserUiState) -> Unit) {
        if (spaces.isNotEmpty()) HorizontalPager(pager, modifier.clipToBounds(), userScrollEnabled = false,
            key = { spaces[it].id }) { page ->
            content(state.copy(settings = state.settings.copy(selectedSpaceId = spaces[page].id)))
        }
    }
    val palette = MaterialTheme.colorScheme
    // Follow the gesture itself, including a cancelled swipe settling back to its Space.
    val position = (pager.currentPage + pager.currentPageOffsetFraction)
        .coerceIn(0f, spaces.lastIndex.coerceAtLeast(0).toFloat())
    val startPage = position.toInt()
    val endPage = (startPage + 1).coerceAtMost(spaces.lastIndex.coerceAtLeast(0))
    val swipeSeed = if (!spacesEnabled) Color(com.takeruf.nagi.ui.theme.themeSeedColor(state.currentSpace?.color, state.settings.themeColor)) else lerp(Color(spaces.getOrNull(startPage)?.color ?: 0xFF426B5A),
        Color(spaces.getOrNull(endPage)?.color ?: 0xFF426B5A), position - startPage)
    val sidebarPalette = themeColorScheme(palette.background.luminance() < 0.5f, swipeSeed.toArgb().toLong())
    val tint = sidebarPalette.background
    val sidebarTop = sidebarPalette.surfaceTint.copy(alpha = 0.02f).compositeOver(tint)
    val sidebarBottom = sidebarPalette.surfaceTint.copy(alpha = 0f).compositeOver(tint)
    fun detachesSplit(item: SidebarDragItem?, destination: SidebarDestination?): Boolean =
        !overlay && item != null && !item.favorite && item.pairRight == null && splitLeft != null && splitRight != null &&
            item.id in listOf(splitLeft.id, splitRight.id) && destination != null &&
            destination.section in listOf(SidebarSection.PINNED, SidebarSection.TODAY) &&
            destination.id != item.id
    fun drop() {
        val item = drag.item ?: return
        val destination = drag.destination ?: return
        if (item.pairRight != null) {
            when (destination.section) {
                SidebarSection.PINNED, SidebarSection.TODAY -> {
                    val pinned = destination.section == SidebarSection.PINNED
                    val target = if (destination.atStart) state.visibleTabs.firstOrNull {
                        it.isPinned == pinned && it.id !in listOf(item.id, item.pairRight.id)
                    }?.id else destination.id
                    vm.dropPair(item.id, pinned, target, destination.after)
                }
                SidebarSection.SPACE -> destination.id?.let { vm.movePair(item.id, it) }
                else -> Unit
            }
            return
        }
        if (detachesSplit(item, destination)) onDetachSplit(item.id)
        when (destination.section) {
            SidebarSection.SPLIT_TAB -> if (!item.favorite && destination.id != null && destination.id != item.id)
                if (destination.after) onSplitPair(destination.id, item.id) else onSplitPair(item.id, destination.id)
            SidebarSection.SPLIT_LEFT, SidebarSection.SPLIT_RIGHT -> if (item.favorite)
                onFavoritePaneDrop(item.id, destination)
            else onSplitDrop(item.id, destination.section == SidebarSection.SPLIT_RIGHT)
            SidebarSection.SPACE -> if (item.favorite) {
                // Favorites stay shared; opening in a Space keeps the shortcut.
                state.favorites.firstOrNull { it.id == item.id }?.let { vm.newTabInSpace(destination.id!!, it.url) }
            } else vm.moveTab(item.id, destination.id!!)
            SidebarSection.FAVORITES -> if (item.favorite) {
                drag.commitFavoriteOrder()
                vm.reorderFavorite(item.id, destination.id, destination.after)
            }
                else vm.favoriteTab(item.id, destination.id, destination.after)
            SidebarSection.PINNED, SidebarSection.TODAY -> {
                val pinned = destination.section == SidebarSection.PINNED
                // Resolve the section start at release, excluding the tab being moved.
                val targetId = if (destination.atStart) state.visibleTabs.firstOrNull {
                    it.isPinned == pinned && it.id != item.id
                }?.id else destination.id
                if (item.favorite) vm.dropFavorite(item.id, pinned, targetId, destination.after)
                else vm.dropTab(item.id, pinned, targetId, destination.after)
            }
        }
    }
    LaunchedEffect(state.currentSpace?.id, collapsed) { drag.cancel() }
    LaunchedEffect(spacesEnabled) {
        drag.cancel()
        if (!spacesEnabled) {
            spacesMenu = false; createSpace = false; editingSpace = null; deleteSpace = null
        }
    }
    val sidebarBackground = Brush.verticalGradient(listOf(sidebarTop, sidebarBottom))
    val panelSize = if (overlay) Modifier.fillMaxWidth() else Modifier.fillMaxSize()
    Box(panelSize.drawBehind {
        // Continue the same gradient into the camera gutter without moving controls
        // or making the reserved camera area interactive.
        val extension = backgroundExtension.toPx()
        if (extension > 0f) drawRect(sidebarBackground,
            topLeft = Offset(-extension, 0f), size = Size(extension, size.height))
    }.clip(NagiShapes.Sidebar).background(sidebarBackground)
        .sidebarSpaceSwipe(drag, enabled = spacesEnabled,
            onStart = { settling?.cancel(); gesturePage = selectedPage },
            onDrag = { delta ->
                // Keep each gesture within the two neighboring Spaces, including at the ends.
                val position = pager.currentPage + pager.currentPageOffsetFraction
                val lower = (gesturePage - 1).coerceAtLeast(0).toFloat()
                val upper = (gesturePage + 1).coerceAtMost(spaces.lastIndex).toFloat()
                val width = pager.layoutInfo.pageSize.toFloat()
                if (width > 0f) pager.dispatchRawDelta(((position - delta / width).coerceIn(lower, upper) - position) * width)
            },
            onFinish = { forward ->
                val next = (gesturePage + when (forward) { true -> 1; false -> -1; null -> 0 })
                    .coerceIn(0, spaces.lastIndex.coerceAtLeast(0))
                settle(next)
                if (next != selectedPage && next in spaces.indices) {
                    vm.selectSpace(spaces[next].id)
                    // Swiping browses the sheet's Space pages; choosing a tab still dismisses it.
                    if (!overlay) onNavigate("browser")
                }
            })
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.firstOrNull()?.let {
                        if (it.type == PointerType.Mouse) mouseInput = true
                        else if (it.pressed) mouseInput = false
                    }
                }
            }
        }
        .sidebarDragHost(drag, { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }, { drop() })) {
        Column(panelSize.padding(horizontal = if (collapsed) 8.dp else 12.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // The panel glyph fills its whole frame, so it is drawn smaller to look the same size
                // as the rounder History/Settings icons.
                ToolButton(if (overlay) NagiIcons.X else if (collapsed) NagiIcons.SidebarOpen else NagiIcons.SidebarClose,
                    if (overlay) strings(R.string.ui_close_1_s, strings(R.string.ui_tabs)) else if (collapsed) strings(R.string.ui_expand_sidebar) else strings(R.string.ui_collapse_sidebar),
                    iconSize = if (overlay) 21.dp else 18.dp) {
                    onToggleSidebar()
                }
                if (!collapsed) {
                    if (overlay) {
                        val name = state.currentSpace?.takeIf { spacesEnabled }?.let(strings::spaceName)
                        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            Text(name ?: strings(R.string.ui_tabs), style = MaterialTheme.typography.titleMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("compact-tab-sheet-title"))
                            val tabCount = state.visibleTabs.size
                            Text(pluralStringResource(R.plurals.ui_tab_count, tabCount, tabCount), style = MaterialTheme.typography.labelSmall,
                                color = palette.onSurfaceVariant)
                        }
                        IconButton(onClick = onNewTab, modifier = Modifier.testTag("new-tab-button")) {
                            Icon(NagiIcons.Plus, strings(R.string.ui_new_tab))
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                        Text("Nagi", style = MaterialTheme.typography.labelLarge, color = palette.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                    }
                }
            }
            if (collapsed) {
                ToolButton(NagiIcons.Search, strings(R.string.ui_search_or_enter_url), onClick = onOmnibox)
                ToolButton(NagiIcons.Plus, strings(R.string.ui_new_tab), onClick = onNewTab)
                SpacePages(Modifier.weight(1f)) { state ->
                    val currentPage = state.currentSpace?.id == spaces.getOrNull(selectedPage)?.id
                    val tabs = state.visibleTabs
                    val pairLefts = tabs.filter { tab -> tabs.any { it.id == tab.splitRightTabId } }
                    val listedTabs = tabs.filter { tab -> pairLefts.none { it.splitRightTabId == tab.id } }
                    val list = rememberLazyListState()
                    var viewport by remember { mutableStateOf(Rect.Zero) }
                    if (currentPage) SidebarAutoScroll(drag, list, viewport)
                    LazyColumn(Modifier.fillMaxSize().onGloballyPositioned { viewport = it.boundsInRoot() }, state = list) { items(listedTabs, key = { it.id }) { tab ->
                        val pair = tab.splitRightTabId?.let { id -> tabs.firstOrNull { it.id == id } }?.let { listOf(tab, it) }.orEmpty()
                        val grouped = pair.size == 2
                        val selected = if (grouped) pair.any { it.id == state.activeTab?.id }
                            else tab.id == state.activeTab?.id
                        val title = if (grouped) pair.joinToString(" / ") { strings.tabTitle(it) } else strings.tabTitle(tab)
                        val item = SidebarDragItem(tab.id, title, tab.faviconUrl, siteUrl = tab.url,
                            pairLeft = tab.takeIf { grouped }, pairRight = pair.getOrNull(1),
                            pairActiveTabId = state.activeTab?.id, collapsed = true)
                        val section = if (tab.isPinned) SidebarSection.PINNED else SidebarSection.TODAY
                        val target = drag.destination?.takeIf { currentPage && it.id == tab.id && drag.item?.id != tab.id }
                        Box(Modifier.padding(vertical = 2.dp).size(dimensions.controlSize)
                            .testTag("collapsed-tab-${tab.id}")
                            .then(if (currentPage) Modifier.sidebarTarget(drag, "collapsed-tab-${tab.id}",
                                SidebarDestination(section, tab.id), canSplit = !grouped) else Modifier)) {
                        Surface(onClick = { selectTab(if (grouped && splitRightFocused) pair[1] else tab) }, shape = NagiShapes.Rounded,
                            color = if (selected) palette.secondaryContainer else Color.Transparent,
                            contentColor = if (selected) palette.onSecondaryContainer else palette.onSurface,
                            modifier = Modifier.fillMaxSize().alpha(if (currentPage && drag.item?.id == tab.id) 0.3f else 1f)
                                .then(if (currentPage) Modifier.sidebarDraggable(drag, item) else Modifier)
                                .then(if (grouped) Modifier.testTag("sidebar-split-group") else Modifier).semantics {
                                contentDescription = strings(R.string.ui_tab_1_s, title)
                                this.selected = selected
                            }) {
                            Box(contentAlignment = Alignment.Center) {
                                if (grouped) {
                                    CollapsedPairIcon(pair)
                                } else CollapsedTabIcon(tab)
                            }
                        }
                        if (target?.section == SidebarSection.SPLIT_TAB) Box(Modifier.matchParentSize()) {
                            Box(Modifier.align(if (target.after) Alignment.CenterEnd else Alignment.CenterStart)
                                .fillMaxWidth(0.5f).fillMaxHeight().testTag("collapsed-tab-split-drop-${tab.id}")
                                .background(palette.primary.copy(alpha = 0.28f), NagiShapes.Rounded))
                        } else if (target?.section == section) Box(Modifier.align(if (target.after) Alignment.BottomCenter else Alignment.TopCenter)
                            .fillMaxWidth().height(3.dp).testTag("collapsed-tab-insertion-${tab.id}").background(palette.primary))
                        }
                    } }
                }
            } else {
                if (!overlay) {
                    SidebarAddressBar(pageUrl, pageTitle, onOmnibox)
                    Spacer(Modifier.height(12.dp))
                }
                if (!overlay) FavoriteGrid(state.favorites, drag, !mouseInput, onOpenUrl, vm::removeBookmark,
                    onEdit = { editingFavoriteId = it.id })
                SpacePages(Modifier.weight(1f, fill = !overlay)) { state ->
                    val tabs = state.visibleTabs
                    val pairLefts = tabs.filter { tab -> tabs.any { it.id == tab.splitRightTabId } }
                    val todayStart = SidebarDestination(SidebarSection.TODAY, atStart = true)
                    val pinnedStart = SidebarDestination(SidebarSection.PINNED, atStart = true)
                    val listedTabs = if (overlay) tabs else tabs.filter { tab -> pairLefts.none { it.splitRightTabId == tab.id } }
                    @Composable
                    fun SidebarTab(tab: BrowserTab, drag: SidebarDragState) {
                        val pairRight = tabs.firstOrNull { !overlay && it.id == tab.splitRightTabId }
                        if (pairRight != null) {
                            val pairItem = SidebarDragItem(tab.id, "${strings.tabTitle(tab)} / ${strings.tabTitle(pairRight)}", tab.faviconUrl,
                                siteUrl = tab.url, pairRight = pairRight, pairLeft = tab,
                                pairActiveTabId = state.activeTab?.id.takeIf { !mouseInput })
                            Surface(Modifier.fillMaxWidth().padding(vertical = 3.dp).testTag("sidebar-split-group")
                                .alpha(if (drag.item?.id == tab.id && drag.item?.pairRight != null) 0.3f else 1f)
                                .sidebarDraggable(drag, pairItem, immediate = true)
                                .sidebarTarget(drag, "pair-${tab.id}", SidebarDestination(if (tab.isPinned) SidebarSection.PINNED else SidebarSection.TODAY, tab.id), canSplit = false),
                                // Match the inner tab radius plus its inset; keep sidebar groups flat.
                                shape = RoundedCornerShape(NagiShapes.Radius + 6.dp), color = palette.surface,
                                contentColor = palette.onSurface) {
                                @Composable
                                fun Member(index: Int, page: BrowserTab, modifier: Modifier = Modifier) {
                                    TabRow(page, state, vm, drag, page.id in loadingIds, !mouseInput,
                                        { selectTab(page) }, { onSplitTab(page.id) },
                                        modifier = modifier, compact = true, replacementRight = index == 1,
                                        suspended = page.id in suspendedIds, largeTouchTargets = overlay)
                                }
                                Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Member(0, tab, Modifier.weight(1f))
                                    Member(1, pairRight, Modifier.weight(1f))
                                }
                            }
                        } else TabRow(tab, state, vm, drag, tab.id in loadingIds, !mouseInput,
                            { selectTab(tab) }, { onSplitTab(tab.id) }, suspended = tab.id in suspendedIds, largeTouchTargets = overlay, splitEnabled = !overlay)
                    }
                    val list = rememberLazyListState()
                    var viewport by remember { mutableStateOf(Rect.Zero) }
                    val openTabs = listedTabs.filterNot { it.isPinned }
                    val pinnedTabs = listedTabs.filter { it.isPinned }
                    val selectedId = state.activeTab?.id
                    val selectedPinned = pinnedTabs.indexOfFirst { selectedId != null && (it.id == selectedId || (!overlay && it.splitRightTabId == selectedId)) }
                    val selectedOpen = openTabs.indexOfFirst { selectedId != null && (it.id == selectedId || (!overlay && it.splitRightTabId == selectedId)) }
                    var pinnedCollapsed by rememberSaveable { mutableStateOf(overlay && pinnedTabs.isNotEmpty() && selectedPinned < 0) }
                    var favoritesExpanded by rememberSaveable { mutableStateOf(false) }
                    LaunchedEffect(state.currentSpace?.id) {
                        if (overlay && selectedId != null) {
                            when {
                                selectedOpen > 2 -> list.scrollToItem(selectedOpen + 1)
                                selectedPinned >= 0 -> {
                                    pinnedCollapsed = false
                                    // Open tabs sit between the two drop zones, then Favorites and Pinned headers.
                                    list.scrollToItem(openTabs.size + (if (favoritesExpanded) 5 else 4) + selectedPinned)
                                }
                            }
                        }
                    }
                    // Only the selected page participates in tab dragging and drop hit testing.
                    val drag = if (state.currentSpace?.id == spaces.getOrNull(selectedPage)?.id) drag else remember { SidebarDragState() }
                    val spaceForeground = readableSpaceColor(Color(state.currentSpace?.color ?: 0xFF426B5A),
                        palette.onSurface, listOf(sidebarTop, sidebarBottom))
                    SidebarAutoScroll(drag, list, viewport)
                    LazyColumn(panelSize.semantics {
                        if (state.currentSpace?.id == spaces.getOrNull(selectedPage)?.id) contentDescription = strings(R.string.ui_sidebar_tabs)
                    }.onGloballyPositioned { viewport = it.boundsInRoot() }, state = list,
                        verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        fun pinnedSection() {
                            item("space-heading") {
                                Row(Modifier.fillMaxWidth().height(52.dp)
                                    .testTag("sidebar-pinned-drop").sidebarTarget(drag, "pinned-header", pinnedStart, canSplit = false), verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { pinnedCollapsed = !pinnedCollapsed }, modifier = Modifier.size(if (overlay) 48.dp else 40.dp)) {
                                        Icon(if (pinnedCollapsed && drag.item == null) NagiIcons.ChevronRight else NagiIcons.ChevronDown,
                                            strings(R.string.ui_toggle_pinned_tabs), Modifier.size(18.dp))
                                    }
                                    if (spacesEnabled && !overlay) SpaceIcon(state.currentSpace?.icon ?: "◉", spaceForeground)
                                    Text(if (spacesEnabled && !overlay) state.currentSpace?.let(strings::spaceName) ?: strings(R.string.ui_space) else strings(R.string.ui_pinned_tabs), Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleSmall)
                                    if (spacesEnabled) Box {
                                        ToolButton(NagiIcons.Ellipsis, strings(R.string.ui_space_actions)) { spacesMenu = true }
                                        NagiOverflowMenu(spacesMenu, { spacesMenu = false }) {
                                            NagiOverflowMenuItem(leadingIcon = { Icon(NagiIcons.Plus, null) }, text = { Text(strings(R.string.ui_new_space)) }, onClick = { createSpace = true; spacesMenu = false })
                                            NagiOverflowMenuItem(leadingIcon = { Icon(NagiIcons.Pencil, null) }, text = { Text(strings(R.string.ui_edit_space)) }, onClick = { editingSpace = state.currentSpace; spacesMenu = false })
                                            NagiOverflowMenuItem(destructive = true, leadingIcon = { Icon(NagiIcons.Trash2, null) }, text = { Text(strings(R.string.ui_delete_space)) }, enabled = state.workspace.spaces.size > 1,
                                                onClick = { deleteSpace = state.currentSpace; spacesMenu = false })
                                        }
                                    }
                                }
                            }
                            if (!pinnedCollapsed || drag.item != null) {
                                items(pinnedTabs, key = { it.id }) { tab -> SidebarTab(tab, drag) }
                                if (pinnedTabs.isEmpty()) item("empty-pinned") {
                                    Box(Modifier.fillMaxWidth().height(42.dp).testTag("sidebar-empty-pinned-drop").sidebarTarget(drag, "empty-pinned", SidebarDestination(SidebarSection.PINNED)), contentAlignment = Alignment.CenterStart) {
                                        if (drag.item == null) Text(strings(R.string.ui_keep_your_everyday_tabs_here), Modifier.padding(start = 12.dp),
                                            style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        fun openSection() {
                            item("today-heading") {
                                Column(Modifier.heightIn(min = if (overlay) 4.dp else 0.dp).testTag("sidebar-today-top-drop").sidebarTarget(drag, "today-header", todayStart, canSplit = false)) {
                                    if (!overlay) HorizontalDivider(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), color = palette.onSurface.copy(alpha = 0.12f))
                                    if (!overlay) Row(Modifier.fillMaxWidth().testTag(if (state.currentSpace?.id == spaces.getOrNull(selectedPage)?.id)
                                        "new-tab-button" else "new-tab-button-${state.currentSpace?.id}").clip(NagiShapes.Rounded)
                                        .clickable(onClick = onNewTab).height(dimensions.rowHeight).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        // Same 40dp icon slot as tab rows, so the + lines up with their icons and titles.
                                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                            Icon(NagiIcons.Plus, null, Modifier.size(20.dp), tint = palette.onSurfaceVariant)
                                        }
                                        Text(strings(R.string.ui_new_tab), Modifier.weight(1f), color = palette.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    if (drag.item != null && drag.destination == todayStart)
                                        Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp).height(3.dp)
                                            .testTag("sidebar-today-top-indicator").background(palette.primary, RoundedCornerShape(2.dp)))
                                }
                            }
                            items(openTabs, key = { it.id }) { tab -> SidebarTab(tab, drag) }
                            item("today-tail") {
                                Box(Modifier.fillMaxWidth().height(if (overlay && drag.item == null) 4.dp else if (overlay) 24.dp else 56.dp).testTag("sidebar-today-drop").sidebarTarget(drag, "today-tail", SidebarDestination(SidebarSection.TODAY))) {
                                    if (drag.item != null && drag.destination == SidebarDestination(SidebarSection.TODAY))
                                        Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp).height(3.dp)
                                            .testTag("sidebar-today-drop-indicator").background(palette.primary, RoundedCornerShape(2.dp)))
                                }
                            }
                        }
                        if (overlay) {
                            openSection()
                            item("favorites-heading") {
                                TextButton(onClick = { favoritesExpanded = !favoritesExpanded },
                                    modifier = Modifier.heightIn(min = dimensions.rowHeight)) {
                                    Icon(if (favoritesExpanded) NagiIcons.ChevronDown else NagiIcons.ChevronRight,
                                        null, Modifier.size(18.dp))
                                    Text(strings(R.string.ui_favorites_section), Modifier.padding(start = 8.dp))
                                }
                            }
                            if (favoritesExpanded || drag.item != null || drag.favoriteLanding != null) item("favorites-grid") {
                                FavoriteGrid(state.favorites, drag, !mouseInput, onOpenUrl, vm::removeBookmark,
                                    onEdit = { editingFavoriteId = it.id })
                            }
                            pinnedSection()
                        } else {
                            pinnedSection()
                            openSection()
                        }
                        val archived = state.workspace.tabs.filter { it.spaceId == state.currentSpace?.id && it.archivedAt != null && it.closedAt == null }
                        if (archived.isNotEmpty()) item("archive") {
                            var expanded by remember { mutableStateOf(false) }
                            Column {
                                TextButton(shape = NagiShapes.Rounded, onClick = { expanded = !expanded }) { Text(strings(R.string.ui_archived_1_s, archived.size), style = MaterialTheme.typography.labelSmall) }
                                if (expanded) archived.forEach { tab -> TextButton(shape = NagiShapes.Rounded, onClick = { selectTab(tab) }) { Text(strings.tabTitle(tab), maxLines = 1) } }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(if (collapsed) 0.dp else 8.dp))
            if (spacesEnabled && collapsed) state.currentSpace?.let { current ->
                val foreground = readableSpaceColor(Color(current.color), palette.onSurface,
                    listOf(sidebarTop, sidebarBottom, palette.surfaceContainerHighest, palette.primaryContainer, palette.secondaryContainer))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val tooltip = rememberTooltipState()
                    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip(Modifier.testTag("collapsed-space-tooltip")) { Text(strings.spaceName(current)) } },
                        state = tooltip) {
                        Box(Modifier.size(dimensions.controlSize).testTag("collapsed-space-switcher")
                            .clip(NagiShapes.Rounded).clickable(role = Role.Button) {
                                scope.launch { tooltip.dismiss() }
                                spaceSwitcherExpanded = true
                            }
                            .semantics { contentDescription = "${strings(R.string.ui_spaces)}: ${strings.spaceName(current)}"; selected = true },
                            contentAlignment = Alignment.Center) {
                            Box(Modifier.size(36.dp).clip(NagiShapes.Rounded).background(palette.secondaryContainer),
                                contentAlignment = Alignment.Center) {
                                SpaceIcon(current.icon, foreground, size = 16.dp)
                            }
                        }
                    }
                    NagiOverflowMenu(spaceSwitcherExpanded, { spaceSwitcherExpanded = false }) {
                        state.workspace.spaces.forEach { space ->
                            val active = space.id == current.id
                            val menuForeground = readableSpaceColor(Color(space.color), palette.onSurface,
                                listOf(palette.surfaceContainerHigh))
                            NagiOverflowMenuItem(
                                modifier = Modifier.testTag("collapsed-space-option-${space.id}")
                                    .semantics { selected = active },
                                leadingIcon = { SpaceIcon(space.icon, menuForeground, size = 16.dp) },
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(strings.spaceName(space), Modifier.weight(1f))
                                        if (active) Icon(NagiIcons.Check, null, Modifier.size(18.dp))
                                    }
                                },
                                onClick = { spaceSwitcherExpanded = false; vm.selectSpace(space.id); onNavigate("browser") },
                            )
                        }
                        NagiOverflowMenuDivider()
                        NagiOverflowMenuItem(leadingIcon = { Icon(NagiIcons.Plus, null) },
                            text = { Text(strings(R.string.ui_new_space)) },
                            onClick = { spaceSwitcherExpanded = false; createSpace = true })
                    }
                }
            } else if (spacesEnabled) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                state.workspace.spaces.forEach { space ->
                    val highlighted = drag.destination?.section == SidebarSection.SPACE && drag.destination?.id == space.id
                    val active = space.id == state.currentSpace?.id
                    val foreground = readableSpaceColor(Color(space.color), palette.onSurface,
                        listOf(sidebarTop, sidebarBottom, palette.surfaceContainerHighest, palette.primaryContainer, palette.secondaryContainer))
                    Box(Modifier.size(dimensions.controlSize).sidebarTarget(drag, "space-${space.id}", SidebarDestination(SidebarSection.SPACE, space.id))
                        .clip(NagiShapes.Rounded)
                        .clickable { vm.selectSpace(space.id); onNavigate("browser") }.semantics { contentDescription = strings(R.string.ui_switch_to_1_s, strings.spaceName(space)); selected = active }, contentAlignment = Alignment.Center) {
                        Box(Modifier.size(36.dp).clip(NagiShapes.Rounded)
                            .background(if (highlighted) palette.primaryContainer else if (active) palette.secondaryContainer else Color.Transparent)
                            .then(if (highlighted) Modifier.border(1.dp, palette.primary, NagiShapes.Rounded) else Modifier),
                            contentAlignment = Alignment.Center) {
                            SpaceIcon(space.icon, foreground, size = 16.dp)
                        }
                    }
                }
                IconButton(onClick = { createSpace = true }, modifier = Modifier.size(dimensions.controlSize)) {
                    Icon(NagiIcons.Plus, strings(R.string.ui_new_space), modifier = Modifier.size(16.dp))
                }
            }
            if (collapsed) {
                ToolButton(NagiIcons.History, strings(R.string.ui_history)) { onNavigate("history") }
                ToolButton(NagiIcons.Settings, strings(R.string.ui_settings)) { onNavigate("settings") }
            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ToolButton(NagiIcons.History, strings(R.string.ui_history)) { onNavigate("history") }
                ToolButton(NagiIcons.Settings, strings(R.string.ui_settings)) { onNavigate("settings") }
            }
        }

    }
    editingFavoriteId?.let { id ->
        state.favorites.firstOrNull { it.id == id }?.let { favorite ->
            key(id) {
                FavoriteEditor(favorite, state.favorites, onDismiss = { editingFavoriteId = null },
                    onSave = { title, url -> vm.editFavorite(id, title, url); editingFavoriteId = null })
            }
        }
    }
    if (createSpace || editingSpace != null) {
        var name by remember(editingSpace) { mutableStateOf(editingSpace?.name ?: "") }
        var icon by remember(editingSpace) { mutableStateOf(defaultSpaceEmoji(editingSpace?.icon ?: "◉")) }
        var customColorEditor by remember(editingSpace) { mutableStateOf(false) }
        var color by remember(editingSpace) { mutableLongStateOf(editingSpace?.color ?: 0xFF426B5A) }
        var saving by remember(editingSpace) { mutableStateOf(false) }
        val previewId = editingSpace?.id
        DisposableEffect(previewId) {
            onDispose { if (!saving) previewId?.let(vm::cancelSpacePreview) }
        }
        LaunchedEffect(previewId, name, icon, color, customColorEditor) {
            if (!customColorEditor) previewId?.let { vm.previewSpace(it, name, icon.trim(), color) }
        }
        AlertDialog(onDismissRequest = { createSpace = false; editingSpace = null }, title = { NagiSystemBars(); Text(if (createSpace) strings(R.string.ui_new_space) else strings(R.string.ui_edit_space)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(strings(R.string.ui_space_name)) }, singleLine = true)
                SpaceIconPicker(icon, onSelect = { icon = it })
                Text(strings(R.string.ui_theme_color), style = MaterialTheme.typography.labelMedium)
                ThemeColorPicker(color, onSelect = { color = it }, onCustom = { customColorEditor = true })
            } },
            confirmButton = { TextButton(shape = NagiShapes.Rounded, enabled = name.isNotBlank() && icon.isNotBlank(), onClick = {
                if (createSpace) vm.createSpace(name, icon.trim(), color) else editingSpace?.let { saving = true; vm.editSpace(it.id, name, icon.trim(), color) }
                createSpace = false; editingSpace = null
            }) { Text(strings(R.string.ui_save)) } }, dismissButton = { TextButton(shape = NagiShapes.Rounded, onClick = { createSpace = false; editingSpace = null }) { Text(strings(R.string.ui_cancel)) } })
        if (customColorEditor) ThemeColorEditor(color, onDismiss = { customColorEditor = false },
            onPreview = { value -> previewId?.let { vm.previewSpace(it, name, icon.trim(), value) } }) {
            color = it
            customColorEditor = false
        }
    }
    deleteSpace?.let { space -> AlertDialog(onDismissRequest = { deleteSpace = null }, title = { NagiSystemBars(); Text(strings(R.string.ui_delete_1_s_137cdc, strings.spaceName(space))) },
        text = { Text(strings(R.string.ui_its_tabs_will_be_removed_shared_favorites_stay_available)) }, confirmButton = { TextButton(shape = NagiShapes.Rounded, onClick = { vm.deleteSpace(space.id); deleteSpace = null }) { Text(strings(R.string.ui_delete)) } },
        dismissButton = { TextButton(shape = NagiShapes.Rounded, onClick = { deleteSpace = null }) { Text(strings(R.string.ui_cancel)) } }) }
}

@Composable
private fun CollapsedTabIcon(tab: BrowserTab) {
    if (tab.url == "about:blank") Icon(NagiIcons.House, null, Modifier.size(20.dp))
    else Favicon(tab.faviconUrl, siteUrl = tab.url)
}

@Composable
private fun CollapsedPairIcon(pair: List<BrowserTab>) {
    Box(Modifier.width(29.dp).height(24.dp).clip(RoundedCornerShape(6.dp))) {
        Row {
            pair.forEachIndexed { index, page ->
                if (index == 1) Spacer(Modifier.width(5.dp))
                // Crop at the seam without squeezing either site's icon.
                Box(Modifier.width(12.dp).height(24.dp).clipToBounds()) {
                    Box(Modifier.wrapContentSize(if (index == 0) Alignment.CenterStart else Alignment.CenterEnd,
                        unbounded = true).requiredSize(24.dp), contentAlignment = Alignment.Center) {
                        CollapsedTabIcon(page)
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.Center).width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
    }
}

/** Slots stay fixed for hit testing while keyed tiles translate into the preview order. */
@Composable
private fun FavoriteGrid(favorites: List<Bookmark>, drag: SidebarDragState, touch: Boolean,
    onOpen: (String) -> Unit, onRemove: (String) -> Unit, onEdit: (Bookmark) -> Unit) {
    val strings = rememberNagiStrings()
    val tileHeight = LocalNagiUiDimensions.current.favoriteHeight
    val stride = tileHeight + 6.dp
    val ids = favorites.map { it.id }
    SideEffect { drag.syncFavorites(ids) }
    // Freeze slot identities until release; Room remains the source of the committed order.
    val order = drag.favoriteOrder?.takeIf { it.toSet() == ids.toSet() } ?: ids
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("favorites-grid")
        .heightIn(min = tileHeight, max = 220.dp)
        .sidebarTarget(drag, "favorites", SidebarDestination(SidebarSection.FAVORITES))
        .verticalScroll(rememberScrollState())) {
        // Fit as many columns as the width allows, then balance rows: 4 favorites form 2 × 2
        // rather than 3 + 1, 5 form 3 + 2.
        val maxColumns = ((maxWidth + 6.dp) / 78.dp).toInt().coerceAtLeast(1)
        val count = favorites.size.coerceAtLeast(1)
        val columns = (count + ((count + maxColumns - 1) / maxColumns) - 1) / ((count + maxColumns - 1) / maxColumns)
        val tileWidth = (maxWidth - 6.dp * (columns - 1)) / columns
        val rows = (favorites.size + columns - 1) / columns
        val density = LocalDensity.current
        fun position(index: Int) = with(density) {
            IntOffset(((tileWidth + 6.dp) * (index % columns)).roundToPx(), (stride * (index / columns)).roundToPx())
        }
        Box(Modifier.fillMaxWidth().height(if (rows == 0) tileHeight else stride * rows - 6.dp)) {
            favorites.forEachIndexed { index, bookmark ->
                key("slot-${bookmark.id}") {
                    Box(Modifier.offset { position(index) }.width(tileWidth).height(tileHeight)
                        .sidebarTarget(drag, "favorite-${bookmark.id}", SidebarDestination(SidebarSection.FAVORITES, bookmark.id)))
                }
            }
            favorites.forEach { bookmark ->
                key(bookmark.id) {
                    // Compose honors the system animator duration scale, including reduced motion.
                    val offset by animateIntOffsetAsState(position(order.indexOf(bookmark.id)),
                        tween(180, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)), label = "favorite-placement")
                    FavoriteTile(bookmark, Modifier.offset { offset }.width(tileWidth), drag, touch,
                        { onOpen(bookmark.url) }, { onRemove(bookmark.id) }, { onEdit(bookmark) })
                }
            }
            if (favorites.isEmpty() && drag.item == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(strings(R.string.ui_drag_a_tab_here_to_favorite),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FavoriteTile(bookmark: Bookmark, modifier: Modifier, drag: SidebarDragState, touch: Boolean,
    onOpen: () -> Unit, onRemove: () -> Unit, onEdit: () -> Unit) {
    val strings = rememberNagiStrings()
    var menu by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val viewConfiguration = LocalViewConfiguration.current
    val menuConfiguration = remember(viewConfiguration) {
        object : ViewConfiguration by viewConfiguration {
            override val minimumTouchTargetSize = DpSize(24.dp, 24.dp)
        }
    }
    val target = drag.destination?.takeIf { it.section == SidebarSection.FAVORITES && it.id == bookmark.id && drag.item?.favorite != true }
    Box(modifier) {
        Box(Modifier.fillMaxWidth().height(LocalNagiUiDimensions.current.favoriteHeight)
            .clip(NagiShapes.Rounded).background(MaterialTheme.colorScheme.surfaceBright)
            .sidebarDraggable(drag, SidebarDragItem(bookmark.id, bookmark.title, bookmark.faviconUrl, true, bookmark.url))
            .hoverable(interaction).sidebarContextMenu { menu = true }
            .clickable(onClick = onOpen).semantics { contentDescription = strings(R.string.ui_favorite_1_s, bookmark.title)
                customActions = listOf(CustomAccessibilityAction(strings(R.string.ui_show_actions)) { menu = true; true }) }, contentAlignment = Alignment.Center) {
            Favicon(bookmark.faviconUrl, siteUrl = bookmark.url, iconSize = FavoriteIconSize)
            Box(Modifier.align(Alignment.TopEnd)) {
                if (hovered || touch || menu) CompositionLocalProvider(
                    LocalViewConfiguration provides menuConfiguration,
                    LocalMinimumInteractiveComponentSize provides 24.dp,
                ) {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(24.dp)
                        .sidebarGestureExclusion(drag, "favorite-actions-${bookmark.id}")) {
                        Icon(NagiIcons.Ellipsis, strings(R.string.ui_favorite_actions_for_1_s, bookmark.title), Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                NagiOverflowMenu(menu, { menu = false }) {
                    NagiOverflowMenuItem(leadingIcon = { Icon(NagiIcons.Pencil, null) },
                        text = { Text(strings(R.string.ui_edit_favorite)) },
                        modifier = Modifier.testTag("edit-favorite-${bookmark.id}"), onClick = { menu = false; onEdit() })
                    NagiOverflowMenuItem(destructive = true, leadingIcon = { Icon(NagiIcons.Trash2, null) },
                        text = { Text(strings(R.string.ui_remove_favorite)) }, onClick = { onRemove(); menu = false })
                }
            }
        }
        if (target != null) Box(Modifier.align(if (target.after) Alignment.CenterEnd else Alignment.CenterStart)
            .width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
    }
}

@Composable
private fun TabRow(tab: BrowserTab, state: BrowserUiState, vm: BrowserViewModel, drag: SidebarDragState,
    loading: Boolean, touch: Boolean, onSelect: () -> Unit, onSplit: () -> Unit,
    modifier: Modifier = Modifier, compact: Boolean = false, selectedOverride: Boolean? = null, suspended: Boolean = false,
    replacementRight: Boolean? = null, largeTouchTargets: Boolean = false, splitEnabled: Boolean = true) {
    val strings = rememberNagiStrings()
    var menu by remember { mutableStateOf(false) }
    val dimensions = LocalNagiUiDimensions.current
    val actionSize = if (dimensions.compact) dimensions.controlSize else if (largeTouchTargets) 48.dp else 40.dp
    val selected = selectedOverride ?: (state.activeTab?.id == tab.id)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val showActions = largeTouchTargets || hovered || menu || (touch && selected)
    val palette = MaterialTheme.colorScheme
    val contentColor = if (selected && !compact) palette.onSecondaryContainer else palette.onSurface
    val section = if (tab.isPinned) SidebarSection.PINNED else SidebarSection.TODAY
    val target = drag.destination?.takeIf { it.section == section && it.id == tab.id && drag.item?.id != tab.id }
    val splitTarget = drag.destination?.section == SidebarSection.SPLIT_TAB && drag.destination?.id == tab.id
    val replacementTarget = drag.destination?.id == tab.id && drag.destination?.section in
        listOf(SidebarSection.SPLIT_LEFT, SidebarSection.SPLIT_RIGHT)
    val item = SidebarDragItem(tab.id, strings.tabTitle(tab), tab.faviconUrl, siteUrl = tab.url)
    Box(modifier.fillMaxWidth().testTag("sidebar-tab-${tab.id}")
        .sidebarTarget(drag, "tab-${tab.id}", SidebarDestination(section, tab.id), canSplit = splitEnabled && !compact, replacementRight = replacementRight.takeIf { splitEnabled })) {
        if (replacementTarget) Box(Modifier.matchParentSize().testTag("sidebar-replace-drop-${tab.id}")
            .background(palette.primary.copy(alpha = 0.28f).compositeOver(palette.surfaceContainerHigh), NagiShapes.Rounded)
            .border(2.dp, palette.primary, NagiShapes.Rounded))
        if (splitTarget) Box(Modifier.matchParentSize()) {
            Box(Modifier.align(if (drag.destination?.after == true) Alignment.CenterEnd else Alignment.CenterStart)
                .fillMaxWidth(0.5f).fillMaxHeight().testTag("sidebar-split-drop-${tab.id}")
                .background(palette.primary.copy(alpha = 0.28f).compositeOver(palette.surfaceContainerHigh), NagiShapes.Rounded))
        }
        Row(Modifier.fillMaxWidth().alpha(if (drag.item?.id == tab.id && drag.item?.pairRight == null) 0.3f else 1f)
            .clip(NagiShapes.Rounded).background(if (splitTarget || replacementTarget) Color.Transparent else if (selected && !compact) palette.secondaryContainer else if (compact) palette.surfaceContainerHigh else Color.Transparent)
            .sidebarDraggable(drag, item).hoverable(interaction).sidebarContextMenu { menu = true }.clickable(onClick = onSelect)
            .heightIn(min = dimensions.rowHeight).semantics { contentDescription = strings(R.string.ui_tab_1_s, strings.tabTitle(tab))
                this.selected = selected
                customActions = buildList {
                    add(CustomAccessibilityAction(strings(R.string.ui_show_actions)) { menu = true; true })
                    add(CustomAccessibilityAction(strings(if (tab.isPinned) R.string.ui_unpin_tab else R.string.ui_pin_tab)) { vm.togglePin(tab.id); true })
                    if (tab.url != "about:blank") add(CustomAccessibilityAction(strings(R.string.ui_add_to_favorites)) { vm.favoriteTab(tab.id); true })
                } }, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).semantics { contentDescription = strings(R.string.ui_drag_1_s, strings.tabTitle(tab)) }
                .sidebarDraggable(drag, item, immediate = true)
                .then(if (compact) Modifier.clickable { menu = true } else Modifier), contentAlignment = Alignment.Center) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else if (tab.url == "about:blank") Icon(NagiIcons.House, strings(R.string.ui_new_tab_home), Modifier.size(20.dp), tint = contentColor)
                else Favicon(tab.faviconUrl, siteUrl = tab.url)
            }

            Text(strings.tabTitle(tab), color = contentColor, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(end = if (compact) 8.dp else 0.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (suspended) Text("zZ", style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp).semantics { contentDescription = strings(R.string.ui_tab_suspended) })
            Box {
                if (showActions) IconButton(onClick = { if (compact) vm.closeTab(tab.id) else menu = true }, modifier = Modifier.size(if (largeTouchTargets) actionSize else if (compact) 24.dp else actionSize)) {
                    Icon(if (compact) NagiIcons.X else NagiIcons.Ellipsis,
                        strings(if (compact) R.string.ui_close_1_s else R.string.ui_actions_for_1_s, strings.tabTitle(tab)),
                        Modifier.size(if (compact) 16.dp else 18.dp), tint = if (selected) contentColor else palette.onSurfaceVariant)
                }
                NagiOverflowMenu(menu, { menu = false }) {
                    if (splitEnabled) NagiOverflowMenuItem(leadingIcon = { Icon(NagiIcons.Columns2, null) }, text = { Text(strings(R.string.ui_open_in_right_pane)) }, onClick = { onSplit(); menu = false })
                    state.workspace.spaces.filter { state.settings.spacesEnabled && it.id != tab.spaceId }.forEach { space ->
                        NagiOverflowMenuItem(leadingIcon = { Icon(NagiIcons.ArrowForward, null) }, text = { Text(strings(R.string.ui_move_to_1_s, strings.spaceName(space))) }, onClick = { vm.moveTab(tab.id, space.id); menu = false })
                    }
                    NagiOverflowMenuDivider()
                    NagiOverflowMenuItem(leadingIcon = { Icon(NagiIcons.X, null) }, text = { Text(strings(R.string.ui_close_tab)) }, onClick = { vm.closeTab(tab.id); menu = false })
                }
            }
            if (showActions && !compact) IconButton(onClick = { vm.closeTab(tab.id) }, modifier = Modifier.size(actionSize)) { Icon(NagiIcons.X, strings(R.string.ui_close_1_s, strings.tabTitle(tab)), Modifier.size(16.dp), tint = contentColor) }
        }
        if (target != null) Box(Modifier.align(if (target.after) Alignment.BottomCenter else Alignment.TopCenter)
            .fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
    }
}

/** Render in the app root so the dragged tab follows the pointer beyond the sidebar. */
@Composable
fun SidebarDragPreview(drag: SidebarDragState, modifier: Modifier = Modifier) {
    var origin by remember { mutableStateOf<Offset?>(null) }
    Box(modifier.fillMaxSize().onGloballyPositioned { origin = it.boundsInRoot().topLeft }) {
        // A sheet's root offset is non-zero. Do not draw one frame at root-relative
        // coordinates before this overlay has received its actual position.
        val previewOrigin = origin ?: return@Box
        (drag.item ?: drag.favoriteLanding?.item)?.let { item ->
            val density = LocalDensity.current
            val offset = drag.point - previewOrigin
            if (item.favorite) {
                val landing = drag.favoriteLanding
                val landingOffset = remember(item.id, landing) { Animatable(offset - drag.grabOffset, Offset.VectorConverter) }
                LaunchedEffect(landing) {
                    if (landing != null) {
                        landingOffset.snapTo(drag.point - previewOrigin - drag.grabOffset)
                        landingOffset.animateTo(landing.topLeft - previewOrigin,
                            tween(180, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)))
                        drag.finishFavoriteLanding()
                    }
                }
                Surface(Modifier.offset {
                    // Gesture updates are direct: only the final placement animates.
                    val tileOffset = if (landing == null) drag.point - previewOrigin - drag.grabOffset else landingOffset.value
                    IntOffset(tileOffset.x.roundToInt(), tileOffset.y.roundToInt())
                }
                    .testTag("favorite-drag-preview")
                    .size(with(density) { drag.sourceSize.x.toDp() }, with(density) { drag.sourceSize.y.toDp() })
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, NagiShapes.Rounded),
                    shape = NagiShapes.Rounded, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 8.dp) {
                    Box(contentAlignment = Alignment.Center) {
                        Favicon(item.favicon, siteUrl = item.siteUrl, iconSize = FavoriteIconSize)
                    }
                }
            } else if (item.collapsed) {
                val lift = with(density) { 12.dp.toPx() }
                Surface(Modifier.offset {
                    val topLeft = drag.point - previewOrigin - drag.grabOffset
                    IntOffset(topLeft.x.roundToInt(), (topLeft.y - lift).roundToInt())
                }.testTag("tab-drag-preview")
                    .size(with(density) { drag.sourceSize.x.toDp() }, with(density) { drag.sourceSize.y.toDp() })
                    .shadow(12.dp, NagiShapes.Rounded, clip = false), shape = NagiShapes.Rounded,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Box(contentAlignment = Alignment.Center) {
                        if (item.pairLeft != null && item.pairRight != null) CollapsedPairIcon(listOf(item.pairLeft, item.pairRight))
                        else if (item.siteUrl == "about:blank") Icon(NagiIcons.House, null, Modifier.size(20.dp))
                        else Favicon(item.favicon, siteUrl = item.siteUrl)
                    }
                }
            } else if (item.pairLeft != null && item.pairRight != null) {
                val palette = MaterialTheme.colorScheme
                val strings = rememberNagiStrings()
                val shape = RoundedCornerShape(NagiShapes.Radius + 6.dp)
                val lift = with(density) { 12.dp.toPx() }
                Surface(Modifier.offset {
                    val topLeft = drag.point - previewOrigin - drag.grabOffset
                    IntOffset(topLeft.x.roundToInt(), (topLeft.y - lift).roundToInt())
                }.testTag("tab-drag-preview")
                    .size(with(density) { drag.sourceSize.x.toDp() }, with(density) { drag.sourceSize.y.toDp() })
                    .shadow(12.dp, shape, clip = false), shape = shape, color = palette.surface,
                    contentColor = palette.onSurface) {
                    Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(item.pairLeft, item.pairRight).forEach { page ->
                            Row(Modifier.weight(1f).fillMaxHeight().testTag("split-drag-preview-${page.id}")
                                .clip(NagiShapes.Rounded).background(palette.surfaceContainerHigh),
                                verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                    if (page.url == "about:blank") Icon(NagiIcons.House, null, Modifier.size(20.dp))
                                    else Favicon(page.faviconUrl, siteUrl = page.url)
                                }
                                Text(strings.tabTitle(page), Modifier.weight(1f).padding(end = 8.dp),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                if (item.pairActiveTabId == page.id) Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                    Icon(NagiIcons.X, null, Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            } else {
                val lift = with(density) { 12.dp.toPx() }
                Surface(Modifier.offset {
                    val topLeft = drag.point - previewOrigin - drag.grabOffset
                    IntOffset(topLeft.x.roundToInt(), (topLeft.y - lift).roundToInt())
                }.testTag("tab-drag-preview")
                    .width(with(density) { drag.sourceSize.x.toDp() }.coerceAtLeast(120.dp))
                    .shadow(12.dp, NagiShapes.Rounded, clip = false), shape = NagiShapes.Rounded,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.94f)) {
                    Row(Modifier.height(with(density) { drag.sourceSize.y.toDp() }.coerceAtLeast(48.dp)),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            if (item.siteUrl == "about:blank") Icon(NagiIcons.House, null, Modifier.size(20.dp))
                            else Favicon(item.favicon, siteUrl = item.siteUrl)
                        }
                        Text(item.title, Modifier.weight(1f), maxLines = 1,
                            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            Icon(NagiIcons.X, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** Favorite tiles show a larger favicon than tab rows; the tile itself keeps its size. */
private val FavoriteIconSize = 22.dp
