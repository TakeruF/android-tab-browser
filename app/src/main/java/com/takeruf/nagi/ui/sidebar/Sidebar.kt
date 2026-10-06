package com.takeruf.nagi.ui.sidebar

import androidx.compose.ui.platform.testTag

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
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.takeruf.nagi.domain.model.*
import com.takeruf.nagi.ui.browser.*
import com.takeruf.nagi.ui.components.*
import com.takeruf.nagi.ui.theme.readableSpaceColor
import com.takeruf.nagi.ui.theme.NagiSystemBars
import kotlin.math.roundToInt

@Composable
fun Sidebar(state: BrowserUiState, vm: BrowserViewModel, loadingIds: Set<String>, onNavigate: (String) -> Unit,
    onNewTab: () -> Unit, onOmnibox: () -> Unit, onOpenUrl: (String) -> Unit, onSplitTab: (String) -> Unit, drag: SidebarDragState, onSplitDrop: (String, Boolean) -> Unit, onSplitPair: (String, String) -> Unit = { _, _ -> },
    splitLeftTabId: String? = null, splitRightTabId: String? = null, splitRightFocused: Boolean = false, onFocusSplit: (Boolean) -> Unit = {}, onDetachSplit: (String) -> Unit = {},
    suspendedIds: Set<String> = emptySet(),
    pageUrl: String = state.activeTab?.url.orEmpty(), pageTitle: String = state.activeTab?.title.orEmpty(),
    onToggleSidebar: () -> Unit = { vm.updateSettings { it.copy(sidebarCollapsed = !it.sidebarCollapsed) } }) {
    val strings = rememberNagiStrings()
    val collapsed = state.settings.sidebarCollapsed
    var spacesMenu by remember { mutableStateOf(false) }
    var editingSpace by remember { mutableStateOf<Space?>(null) }
    var createSpace by remember { mutableStateOf(false) }
    var deleteSpace by remember { mutableStateOf<Space?>(null) }
    var mouseInput by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val spaces = state.workspace.spaces
    val splitLeft = state.visibleTabs.firstOrNull { it.id == splitLeftTabId }
    val splitRight = state.visibleTabs.firstOrNull { it.id == splitRightTabId && it.id != splitLeft?.id }
    fun selectTab(tab: BrowserTab) {
        if (splitRight != null && (tab.id == splitLeft?.id || tab.id == splitRight.id)) {
            onFocusSplit(tab.id == splitRight.id)
            vm.accessTab(tab.id)
        } else vm.selectTab(tab.id)
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
        settling = scope.launch { pager.animateScrollToPage(page, animationSpec = settleSpec) }
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
    val swipeSeed = lerp(Color(spaces.getOrNull(startPage)?.color ?: 0xFF426B5A),
        Color(spaces.getOrNull(endPage)?.color ?: 0xFF426B5A), position - startPage)
    val sidebarPalette = themeColorScheme(palette.background.luminance() < 0.5f, swipeSeed.toArgb().toLong())
    val tint = sidebarPalette.background
    val sidebarTop = sidebarPalette.surfaceTint.copy(alpha = 0.10f).compositeOver(tint)
    val sidebarBottom = sidebarPalette.surfaceTint.copy(alpha = 0.03f).compositeOver(tint)
    fun detachesSplit(item: SidebarDragItem?, destination: SidebarDestination?): Boolean =
        item != null && !item.favorite && splitLeft != null && splitRight != null &&
            item.id in listOf(splitLeft.id, splitRight.id) && destination != null &&
            destination.section in listOf(SidebarSection.PINNED, SidebarSection.TODAY) &&
            destination.id != item.id
    fun drop() {
        val item = drag.item ?: return
        val destination = drag.destination ?: return
        if (detachesSplit(item, destination)) onDetachSplit(item.id)
        when (destination.section) {
            SidebarSection.SPLIT_TAB -> if (!item.favorite && destination.id != null && destination.id != item.id)
                if (destination.after) onSplitPair(destination.id, item.id) else onSplitPair(item.id, destination.id)
            SidebarSection.SPLIT_LEFT, SidebarSection.SPLIT_RIGHT -> if (!item.favorite)
                onSplitDrop(item.id, destination.section == SidebarSection.SPLIT_RIGHT)
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
    Box(Modifier.fillMaxSize().clip(NagiShapes.Sidebar).background(Brush.verticalGradient(listOf(sidebarTop, sidebarBottom)))
        .sidebarSpaceSwipe(drag,
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
                    onNavigate("browser")
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
        Column(Modifier.fillMaxSize().padding(horizontal = if (collapsed) 8.dp else 12.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ToolButton(if (collapsed) NagiIcons.SidebarOpen else NagiIcons.SidebarClose, if (collapsed) strings(R.string.ui_expand_sidebar) else strings(R.string.ui_collapse_sidebar)) {
                    onToggleSidebar()
                }
                if (!collapsed) {
                    Spacer(Modifier.weight(1f))
                    Text("Nagi", style = MaterialTheme.typography.labelLarge, color = palette.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                }
            }
            if (collapsed) {
                ToolButton(NagiIcons.Search, strings(R.string.ui_search_or_enter_url), onClick = onOmnibox)
                ToolButton(NagiIcons.Plus, strings(R.string.ui_new_tab), onClick = onNewTab)
                SpacePages(Modifier.weight(1f)) { state ->
                    val tabs = state.visibleTabs
                    val pair = if (splitLeft != null && splitRight != null &&
                        tabs.any { it.id == splitLeft.id } && tabs.any { it.id == splitRight.id }) {
                        listOf(splitLeft, splitRight)
                    } else emptyList()
                    val listedTabs = tabs.filter { it.id != pair.getOrNull(1)?.id }
                    LazyColumn(Modifier.fillMaxSize()) { items(listedTabs, key = { it.id }) { tab ->
                        val grouped = pair.firstOrNull()?.id == tab.id
                        val selected = if (grouped) pair.any { it.id == state.activeTab?.id }
                            else tab.id == state.activeTab?.id
                        Surface(onClick = { selectTab(if (grouped && splitRightFocused) pair[1] else tab) }, shape = NagiShapes.Rounded,
                            color = if (selected) palette.primaryContainer else Color.Transparent,
                            contentColor = palette.onSurface,
                            border = if (selected) BorderStroke(1.dp, palette.primary) else null,
                            modifier = Modifier.padding(vertical = 2.dp).size(48.dp)
                                .then(if (grouped) Modifier.testTag("sidebar-split-group") else Modifier).semantics {
                                contentDescription = strings(R.string.ui_tab_1_s,
                                    if (grouped) pair.joinToString(" / ") { strings.tabTitle(it) } else strings.tabTitle(tab))
                                this.selected = selected
                            }) {
                            Box(contentAlignment = Alignment.Center) {
                                if (grouped) {
                                    Box(Modifier.width(29.dp).height(24.dp).clip(RoundedCornerShape(6.dp))) {
                                        Row {
                                            pair.forEachIndexed { index, page ->
                                                // Leave 2dp on either side of the 1dp center divider.
                                                if (index == 1) Spacer(Modifier.width(5.dp))
                                                // Crop each full-sized icon at the seam; never squeeze it.
                                                Box(Modifier.width(12.dp).height(24.dp).clipToBounds()) {
                                                    Box(Modifier.wrapContentSize(
                                                        if (index == 0) Alignment.CenterStart else Alignment.CenterEnd,
                                                        unbounded = true).requiredSize(24.dp), contentAlignment = Alignment.Center) {
                                                        CollapsedTabIcon(page)
                                                    }
                                                }
                                            }
                                        }
                                        Box(Modifier.align(Alignment.Center).width(1.dp).fillMaxHeight()
                                            .background(palette.outline))
                                    }
                                } else CollapsedTabIcon(tab)
                            }
                        }
                    } }
                }
            } else {
                SidebarAddressBar(pageUrl, pageTitle, onOmnibox)
                Spacer(Modifier.height(12.dp))
                FavoriteGrid(state.favorites, drag, !mouseInput, onOpenUrl, vm::removeBookmark)
                SpacePages(Modifier.weight(1f)) { state ->
                    val tabs = state.visibleTabs
                    val grouped = splitRight != null && splitLeft != null && splitLeft.spaceId == state.currentSpace?.id
                    val todayStart = SidebarDestination(SidebarSection.TODAY, atStart = true)
                    val pinnedStart = SidebarDestination(SidebarSection.PINNED, atStart = true)
                    val listedTabs = if (grouped) tabs.filter { it.id != splitRight?.id } else tabs
                    @Composable
                    fun SidebarTab(tab: BrowserTab, drag: SidebarDragState) {
                        if (grouped && tab.id == splitLeft?.id) {
                            Surface(Modifier.fillMaxWidth().padding(vertical = 3.dp).testTag("sidebar-split-group"),
                                // Match the inner tab radius plus its inset; keep sidebar groups flat.
                                shape = RoundedCornerShape(NagiShapes.Radius + 6.dp), color = palette.surface,
                                contentColor = palette.onSurface) {
                                Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(splitLeft!!, splitRight!!).forEachIndexed { index, page ->
                                        TabRow(page, state, vm, drag, page.id in loadingIds, !mouseInput,
                                            { selectTab(page) }, { onSplitTab(page.id) },
                                            modifier = Modifier.weight(1f), compact = true, replacementRight = index == 1, suspended = page.id in suspendedIds,
                                            selectedOverride = state.activeTab?.id in listOf(splitLeft?.id, splitRight?.id) &&
                                                splitRightFocused == (index == 1))
                                    }
                                }
                            }
                        } else TabRow(tab, state, vm, drag, tab.id in loadingIds, !mouseInput,
                            { selectTab(tab) }, { onSplitTab(tab.id) }, suspended = tab.id in suspendedIds)
                    }
                    val list = rememberLazyListState()
                    var viewport by remember { mutableStateOf(Rect.Zero) }
                    var pinnedCollapsed by rememberSaveable { mutableStateOf(false) }
                    // Only the selected page participates in tab dragging and drop hit testing.
                    val drag = if (state.currentSpace?.id == spaces.getOrNull(selectedPage)?.id) drag else remember { SidebarDragState() }
                    val spaceForeground = readableSpaceColor(Color(state.currentSpace?.color ?: 0xFF426B5A),
                        palette.onSurface, listOf(sidebarTop, sidebarBottom))
                    SidebarAutoScroll(drag, list, viewport)
                    LazyColumn(Modifier.fillMaxSize().semantics {
                        if (state.currentSpace?.id == spaces.getOrNull(selectedPage)?.id) contentDescription = strings(R.string.ui_sidebar_tabs)
                    }.onGloballyPositioned { viewport = it.boundsInRoot() }, state = list,
                        verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        item("space-heading") {
                            Row(Modifier.fillMaxWidth().height(52.dp)
                                .testTag("sidebar-pinned-drop").sidebarTarget(drag, "pinned-header", pinnedStart, canSplit = false), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { pinnedCollapsed = !pinnedCollapsed }, modifier = Modifier.size(40.dp)) {
                                    Icon(if (pinnedCollapsed && drag.item == null) NagiIcons.ChevronRight else NagiIcons.ChevronDown,
                                        strings(R.string.ui_toggle_pinned_tabs), Modifier.size(18.dp))
                                }
                                SpaceIcon(state.currentSpace?.icon ?: "◉", spaceForeground)
                                Text(state.currentSpace?.let(strings::spaceName) ?: strings(R.string.ui_space), Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleSmall)
                                Box {
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
                            items(listedTabs.filter { it.isPinned }, key = { it.id }) { tab -> SidebarTab(tab, drag) }
                            if (listedTabs.none { it.isPinned }) item("empty-pinned") {
                                Box(Modifier.fillMaxWidth().height(42.dp).sidebarTarget(drag, "empty-pinned", SidebarDestination(SidebarSection.PINNED)), contentAlignment = Alignment.CenterStart) {
                                    if (drag.item == null) Text(strings(R.string.ui_keep_your_everyday_tabs_here), Modifier.padding(start = 12.dp),
                                        style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
                                }
                            }
                        }
                        item("today-heading") {
                            Column(Modifier.testTag("sidebar-today-top-drop").sidebarTarget(drag, "today-header", todayStart, canSplit = false)) {
                                HorizontalDivider(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), color = palette.onSurface.copy(alpha = 0.12f))
                                Row(Modifier.fillMaxWidth().testTag(if (state.currentSpace?.id == spaces.getOrNull(selectedPage)?.id)
                                    "new-tab-button" else "new-tab-button-${state.currentSpace?.id}").clip(NagiShapes.Rounded)
                                    .clickable(onClick = onNewTab).height(48.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(NagiIcons.Plus, null, Modifier.size(20.dp), tint = palette.onSurfaceVariant)
                                    Text(strings(R.string.ui_new_tab), Modifier.weight(1f).padding(start = 12.dp), color = palette.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                    Text("Ctrl T", style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)
                                }
                                if (drag.item != null && drag.destination == todayStart)
                                    Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp).height(3.dp)
                                        .testTag("sidebar-today-top-indicator").background(palette.primary, RoundedCornerShape(2.dp)))
                            }
                        }
                        items(listedTabs.filter { !it.isPinned }, key = { it.id }) { tab -> SidebarTab(tab, drag) }
                        item("today-tail") {
                            Box(Modifier.fillMaxWidth().height(56.dp).testTag("sidebar-today-drop").sidebarTarget(drag, "today-tail", SidebarDestination(SidebarSection.TODAY))) {
                                if (drag.item != null && drag.destination == SidebarDestination(SidebarSection.TODAY))
                                    Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp).height(3.dp)
                                        .testTag("sidebar-today-drop-indicator").background(palette.primary, RoundedCornerShape(2.dp)))
                            }
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
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                state.workspace.spaces.forEach { space ->
                    val highlighted = drag.destination?.section == SidebarSection.SPACE && drag.destination?.id == space.id
                    val active = space.id == state.currentSpace?.id
                    val foreground = readableSpaceColor(Color(space.color), palette.onSurface,
                        listOf(sidebarTop, sidebarBottom, palette.surfaceContainerHighest, palette.primaryContainer))
                    Box(Modifier.size(48.dp).sidebarTarget(drag, "space-${space.id}", SidebarDestination(SidebarSection.SPACE, space.id))
                        .clip(NagiShapes.Rounded)
                        .clickable { vm.selectSpace(space.id); onNavigate("browser") }.semantics { contentDescription = strings(R.string.ui_switch_to_1_s, strings.spaceName(space)); selected = active }, contentAlignment = Alignment.Center) {
                        Box(Modifier.size(36.dp).clip(NagiShapes.Rounded)
                            .background(if (highlighted) palette.primaryContainer else if (active) palette.surfaceContainerHighest else Color.Transparent)
                            .then(if (active || highlighted) Modifier.border(1.dp, if (highlighted) palette.primary else foreground, NagiShapes.Rounded) else Modifier),
                            contentAlignment = Alignment.Center) {
                            SpaceIcon(space.icon, foreground, size = 16.dp)
                        }
                    }
                }
                IconButton(onClick = { createSpace = true }, modifier = Modifier.size(48.dp)) {
                    Icon(NagiIcons.Plus, strings(R.string.ui_new_space), modifier = Modifier.size(16.dp))
                }
            }
            if (collapsed) {
                ToolButton(NagiIcons.Bookmark, strings(R.string.ui_bookmarks)) { onNavigate("bookmarks") }
                ToolButton(NagiIcons.History, strings(R.string.ui_history)) { onNavigate("history") }
                ToolButton(NagiIcons.Settings, strings(R.string.ui_settings)) { onNavigate("settings") }
            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ToolButton(NagiIcons.Bookmark, strings(R.string.ui_bookmarks)) { onNavigate("bookmarks") }
                ToolButton(NagiIcons.History, strings(R.string.ui_history)) { onNavigate("history") }
                ToolButton(NagiIcons.Settings, strings(R.string.ui_settings)) { onNavigate("settings") }
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf(0xFF426B5A, 0xFF6C6193, 0xFFB07D47, 0xFF477F96, 0xFF995C77, 0xFF687081).forEachIndexed { index, value ->
                    Box(Modifier.size(48.dp).padding(6.dp).clip(NagiShapes.Rounded).background(Color(value))
                        .clickable { color = value }.testTag("space-color-$value").semantics { selected = color == value; contentDescription = strings(R.string.ui_space_theme_1_s, index + 1) }, contentAlignment = Alignment.Center) {
                        if (color == value) Icon(NagiIcons.Check, null, Modifier.size(18.dp), tint = Color.White)
                    }
                } }
                TextButton(shape = NagiShapes.Rounded, onClick = { customColorEditor = true }) {
                    Icon(NagiIcons.Palette, null, Modifier.size(18.dp))
                    Text(strings(R.string.ui_custom_color_1_s, "%06X".format(color and 0xFFFFFF)), Modifier.padding(start = 8.dp))
                }
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

/** Slots stay fixed for hit testing while keyed tiles translate into the preview order. */
@Composable
private fun FavoriteGrid(favorites: List<Bookmark>, drag: SidebarDragState, touch: Boolean,
    onOpen: (String) -> Unit, onRemove: (String) -> Unit) {
    val strings = rememberNagiStrings()
    val ids = favorites.map { it.id }
    SideEffect { drag.syncFavorites(ids) }
    // Freeze slot identities until release; Room remains the source of the committed order.
    val order = drag.favoriteOrder?.takeIf { it.toSet() == ids.toSet() } ?: ids
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("favorites-grid")
        .heightIn(min = 58.dp, max = 220.dp)
        .sidebarTarget(drag, "favorites", SidebarDestination(SidebarSection.FAVORITES))
        .verticalScroll(rememberScrollState())) {
        val columns = ((maxWidth + 6.dp) / 78.dp).toInt().coerceAtLeast(1)
            .coerceAtMost(favorites.size.coerceAtLeast(1))
        val tileWidth = (maxWidth - 6.dp * (columns - 1)) / columns
        val rows = (favorites.size + columns - 1) / columns
        val density = LocalDensity.current
        fun position(index: Int) = with(density) {
            IntOffset(((tileWidth + 6.dp) * (index % columns)).roundToPx(), (64.dp * (index / columns)).roundToPx())
        }
        Box(Modifier.fillMaxWidth().height(if (rows == 0) 58.dp else 64.dp * rows - 6.dp)) {
            favorites.forEachIndexed { index, bookmark ->
                key("slot-${bookmark.id}") {
                    Box(Modifier.offset { position(index) }.width(tileWidth).height(58.dp)
                        .sidebarTarget(drag, "favorite-${bookmark.id}", SidebarDestination(SidebarSection.FAVORITES, bookmark.id)))
                }
            }
            favorites.forEach { bookmark ->
                key(bookmark.id) {
                    // Compose honors the system animator duration scale, including reduced motion.
                    val offset by animateIntOffsetAsState(position(order.indexOf(bookmark.id)),
                        tween(180, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)), label = "favorite-placement")
                    FavoriteTile(bookmark, Modifier.offset { offset }.width(tileWidth), drag, touch,
                        { onOpen(bookmark.url) }, { onRemove(bookmark.id) })
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
private fun FavoriteTile(bookmark: Bookmark, modifier: Modifier, drag: SidebarDragState, touch: Boolean, onOpen: () -> Unit, onRemove: () -> Unit) {
    val strings = rememberNagiStrings()
    var menu by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val target = drag.destination?.takeIf { it.section == SidebarSection.FAVORITES && it.id == bookmark.id && drag.item?.favorite != true }
    Box(modifier) {
        Box(Modifier.fillMaxWidth().height(58.dp).alpha(if (drag.item?.id == bookmark.id || drag.favoriteLanding?.item?.id == bookmark.id) 0f else 1f)
            .clip(NagiShapes.Rounded).background(MaterialTheme.colorScheme.surfaceContainer)
            .sidebarDraggable(drag, SidebarDragItem(bookmark.id, bookmark.title, bookmark.faviconUrl, true, bookmark.url))
            .hoverable(interaction).sidebarContextMenu { menu = true }
            .clickable(onClick = onOpen).semantics { contentDescription = strings(R.string.ui_favorite_1_s, bookmark.title)
                customActions = listOf(CustomAccessibilityAction(strings(R.string.ui_show_actions)) { menu = true; true }) }, contentAlignment = Alignment.Center) {
            Favicon(bookmark.faviconUrl, siteUrl = bookmark.url, fallbackText = bookmark.title)
            Box(Modifier.align(Alignment.TopEnd)) {
                if (hovered || touch || menu) IconButton(onClick = { menu = true }, modifier = Modifier.size(24.dp)) {
                    Icon(NagiIcons.Ellipsis, strings(R.string.ui_favorite_actions_for_1_s, bookmark.title), Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                NagiOverflowMenu(menu, { menu = false }) { NagiOverflowMenuItem(destructive = true, leadingIcon = { Icon(NagiIcons.Trash2, null) }, text = { Text(strings(R.string.ui_remove_favorite)) }, onClick = { onRemove(); menu = false }) }
            }
        }
        if (target != null) Box(Modifier.align(if (target.after) Alignment.CenterEnd else Alignment.CenterStart)
            .width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
    }
}

@Composable
private fun TabRow(tab: BrowserTab, state: BrowserUiState, vm: BrowserViewModel, drag: SidebarDragState,
    loading: Boolean, touch: Boolean, onSelect: () -> Unit, onSplit: () -> Unit,
    modifier: Modifier = Modifier, compact: Boolean = false, selectedOverride: Boolean? = null, suspended: Boolean = false, replacementRight: Boolean? = null) {
    val strings = rememberNagiStrings()
    var menu by remember { mutableStateOf(false) }
    val selected = selectedOverride ?: (state.activeTab?.id == tab.id)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val showActions = hovered || menu || (touch && selected)
    val palette = MaterialTheme.colorScheme
    val contentColor = if (selected && !compact) palette.onPrimaryContainer else palette.onSurface
    val section = if (tab.isPinned) SidebarSection.PINNED else SidebarSection.TODAY
    val target = drag.destination?.takeIf { it.section == section && it.id == tab.id && drag.item?.id != tab.id }
    val splitTarget = drag.destination?.section == SidebarSection.SPLIT_TAB && drag.destination?.id == tab.id
    val replacementTarget = drag.destination?.id == tab.id && drag.destination?.section in
        listOf(SidebarSection.SPLIT_LEFT, SidebarSection.SPLIT_RIGHT)
    val item = SidebarDragItem(tab.id, strings.tabTitle(tab), tab.faviconUrl, siteUrl = tab.url)
    Box(modifier.fillMaxWidth().testTag("sidebar-tab-${tab.id}")
        .sidebarTarget(drag, "tab-${tab.id}", SidebarDestination(section, tab.id), canSplit = !compact, replacementRight = replacementRight)) {
        if (replacementTarget) Box(Modifier.matchParentSize().testTag("sidebar-replace-drop-${tab.id}")
            .background(palette.primary.copy(alpha = 0.28f).compositeOver(palette.surfaceContainerHigh), NagiShapes.Rounded)
            .border(2.dp, palette.primary, NagiShapes.Rounded))
        if (splitTarget) Box(Modifier.matchParentSize()) {
            Box(Modifier.align(if (drag.destination?.after == true) Alignment.CenterEnd else Alignment.CenterStart)
                .fillMaxWidth(0.5f).fillMaxHeight().testTag("sidebar-split-drop-${tab.id}")
                .background(palette.primary.copy(alpha = 0.28f).compositeOver(palette.surfaceContainerHigh), NagiShapes.Rounded))
        }
        Row(Modifier.fillMaxWidth().alpha(if (drag.item?.id == tab.id) 0.3f else 1f)
            .clip(NagiShapes.Rounded).background(if (splitTarget || replacementTarget) Color.Transparent else if (compact) palette.surfaceContainerHigh else if (selected) palette.primaryContainer else Color.Transparent)
            .then(if (selected && !compact && !splitTarget) Modifier.border(1.dp, palette.primary, NagiShapes.Rounded) else Modifier)
            .sidebarDraggable(drag, item).hoverable(interaction).sidebarContextMenu { menu = true }.clickable(onClick = onSelect)
            .heightIn(min = 48.dp).semantics { contentDescription = strings(R.string.ui_tab_1_s, strings.tabTitle(tab)); this.selected = selected
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
                if (showActions) IconButton(onClick = { if (compact) vm.closeTab(tab.id) else menu = true }, modifier = Modifier.size(if (compact) 24.dp else 40.dp)) {
                    Icon(if (compact) NagiIcons.X else NagiIcons.Ellipsis,
                        strings(if (compact) R.string.ui_close_1_s else R.string.ui_actions_for_1_s, strings.tabTitle(tab)),
                        Modifier.size(if (compact) 16.dp else 18.dp), tint = if (selected) contentColor else palette.onSurfaceVariant)
                }
                NagiOverflowMenu(menu, { menu = false }, modifier = Modifier.width((state.settings.sidebarWidth - 24f).dp)) {
                    NagiOverflowMenuItem(leadingIcon = { Icon(NagiIcons.Columns2, null) }, text = { Text(strings(R.string.ui_open_in_right_pane)) }, onClick = { onSplit(); menu = false })
                    state.workspace.spaces.filter { it.id != tab.spaceId }.forEach { space ->
                        NagiOverflowMenuItem(leadingIcon = { Icon(NagiIcons.ArrowForward, null) }, text = { Text(strings(R.string.ui_move_to_1_s, strings.spaceName(space))) }, onClick = { vm.moveTab(tab.id, space.id); menu = false })
                    }
                    NagiOverflowMenuDivider()
                    NagiOverflowMenuItem(leadingIcon = { Icon(NagiIcons.X, null) }, text = { Text(strings(R.string.ui_close_tab)) }, onClick = { vm.closeTab(tab.id); menu = false })
                }
            }
            if (showActions && !compact) IconButton(onClick = { vm.closeTab(tab.id) }, modifier = Modifier.size(40.dp)) { Icon(NagiIcons.X, strings(R.string.ui_close_1_s, strings.tabTitle(tab)), Modifier.size(16.dp), tint = contentColor) }
        }
        if (target != null) Box(Modifier.align(if (target.after) Alignment.BottomCenter else Alignment.TopCenter)
            .fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
    }
}

/** Render in the app root so the dragged tab follows the pointer beyond the sidebar. */
@Composable
fun SidebarDragPreview(drag: SidebarDragState) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.boundsInRoot().topLeft }) {
        (drag.item ?: drag.favoriteLanding?.item)?.let { item ->
            val density = LocalDensity.current
            val offset = drag.point - origin
            if (item.favorite) {
                val landing = drag.favoriteLanding
                val landingOffset = remember(item.id, landing) { Animatable(offset - drag.grabOffset, Offset.VectorConverter) }
                LaunchedEffect(landing) {
                    if (landing != null) {
                        landingOffset.snapTo(drag.point - origin - drag.grabOffset)
                        landingOffset.animateTo(landing.topLeft - origin,
                            tween(180, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)))
                        drag.finishFavoriteLanding()
                    }
                }
                Surface(Modifier.offset {
                    // Gesture updates are direct: only the final placement animates.
                    val tileOffset = if (landing == null) drag.point - origin - drag.grabOffset else landingOffset.value
                    IntOffset(tileOffset.x.roundToInt(), tileOffset.y.roundToInt())
                }
                    .testTag("favorite-drag-preview")
                    .size(with(density) { drag.sourceSize.x.toDp() }, with(density) { drag.sourceSize.y.toDp() })
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, NagiShapes.Rounded),
                    shape = NagiShapes.Rounded, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 8.dp) {
                    Box(contentAlignment = Alignment.Center) {
                        Favicon(item.favicon, siteUrl = item.siteUrl, fallbackText = item.title)
                    }
                }
            } else {
                val lift = with(density) { 12.dp.toPx() }
                Surface(Modifier.offset {
                    val topLeft = drag.point - origin - drag.grabOffset
                    IntOffset(topLeft.x.roundToInt(), (topLeft.y - lift).roundToInt())
                }.testTag("tab-drag-preview")
                    .width(with(density) { drag.sourceSize.x.toDp() }.coerceAtLeast(120.dp))
                    .shadow(12.dp, NagiShapes.Rounded, clip = false), shape = NagiShapes.Rounded,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.94f)) {
                    Row(Modifier.height(with(density) { drag.sourceSize.y.toDp() }.coerceAtLeast(48.dp)),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            if (item.siteUrl == "about:blank") Icon(NagiIcons.House, null, Modifier.size(20.dp))
                            else Favicon(item.favicon, siteUrl = item.siteUrl, fallbackText = item.title)
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
