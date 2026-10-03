package com.orbit.browser.ui.sidebar

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuOpen
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import com.orbit.browser.domain.model.*
import com.orbit.browser.ui.browser.*
import com.orbit.browser.ui.components.*
import com.orbit.browser.ui.theme.readableSpaceColor
import com.orbit.browser.ui.theme.OrbitSystemBars
import kotlin.math.roundToInt

@Composable
fun Sidebar(state: BrowserUiState, vm: BrowserViewModel, loadingIds: Set<String>, onNavigate: (String) -> Unit,
    onOmnibox: () -> Unit, onOpenUrl: (String) -> Unit, onSplitTab: (String) -> Unit) {
    val collapsed = state.settings.sidebarCollapsed
    var spacesMenu by remember { mutableStateOf(false) }
    var editingSpace by remember { mutableStateOf<Space?>(null) }
    var createSpace by remember { mutableStateOf(false) }
    var deleteSpace by remember { mutableStateOf<Space?>(null) }
    var pinnedCollapsed by rememberSaveable(state.currentSpace?.id) { mutableStateOf(false) }
    val drag = remember { SidebarDragState() }
    val haptic = LocalHapticFeedback.current
    val list = rememberLazyListState()
    var viewport by remember { mutableStateOf(Rect.Zero) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    val tabs = state.visibleTabs
    val spaceColor = Color(state.currentSpace?.color ?: 0xFF426B5A)
    val palette = MaterialTheme.colorScheme
    val tint = palette.background
    val sidebarTop = spaceColor.copy(alpha = 0.17f).compositeOver(tint)
    val sidebarBottom = spaceColor.copy(alpha = 0.07f).compositeOver(tint)
    val spaceForeground = readableSpaceColor(spaceColor, palette.onSurface, listOf(sidebarTop, sidebarBottom))
    fun drop() {
        val item = drag.item ?: return
        val destination = drag.destination ?: return
        when (destination.section) {
            SidebarSection.SPACE -> if (item.favorite) {
                // Favorites stay shared; opening in a Space keeps the shortcut.
                state.favorites.firstOrNull { it.id == item.id }?.let { vm.newTabInSpace(destination.id!!, it.url) }
            } else vm.moveTab(item.id, destination.id!!)
            SidebarSection.FAVORITES -> if (item.favorite) vm.reorderFavorite(item.id, destination.id, destination.after)
                else vm.favoriteTab(item.id, destination.id, destination.after)
            SidebarSection.PINNED, SidebarSection.TODAY -> if (item.favorite)
                vm.dropFavorite(item.id, destination.section == SidebarSection.PINNED, destination.id, destination.after)
                else vm.dropTab(item.id, destination.section == SidebarSection.PINNED, destination.id, destination.after)
        }
    }
    LaunchedEffect(state.currentSpace?.id, collapsed) { drag.cancel() }
    SidebarAutoScroll(drag, list, viewport)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(sidebarTop, sidebarBottom)))
        .onGloballyPositioned { rootOrigin = it.boundsInRoot().topLeft }
        .sidebarDragHost(drag, { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }, { drop() })) {
        Column(Modifier.fillMaxSize().padding(horizontal = if (collapsed) 8.dp else 12.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ToolButton(Icons.AutoMirrored.Outlined.MenuOpen, if (collapsed) "Expand sidebar" else "Collapse sidebar") {
                    vm.updateSettings { it.copy(sidebarCollapsed = !it.sidebarCollapsed) }
                }
                if (!collapsed) {
                    Spacer(Modifier.weight(1f))
                    Text("orbit", style = MaterialTheme.typography.labelLarge, color = palette.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                }
            }
            if (collapsed) {
                ToolButton(Icons.Outlined.Search, "Search or enter URL", onClick = onOmnibox)
                ToolButton(Icons.Outlined.Add, "New tab") { vm.newTab(); onNavigate("browser") }
                LazyColumn(Modifier.weight(1f)) { items(tabs, key = { it.id }) { tab ->
                    Surface(onClick = { vm.selectTab(tab.id); onNavigate("browser") }, shape = RoundedCornerShape(10.dp),
                        color = if (tab.id == state.activeTab?.id) palette.primaryContainer else Color.Transparent,
                        contentColor = palette.onSurface,
                        border = if (tab.id == state.activeTab?.id) BorderStroke(1.dp, palette.primary) else null,
                        modifier = Modifier.padding(vertical = 2.dp).size(48.dp).semantics {
                            contentDescription = "Tab ${tab.title}"
                            selected = tab.id == state.activeTab?.id
                        }) {
                        Box(contentAlignment = Alignment.Center) { Favicon(tab.faviconUrl) }
                    }
                } }
            } else {
                Surface(onClick = onOmnibox, shape = RoundedCornerShape(12.dp), color = palette.surfaceContainer,
                    contentColor = palette.onSurface, border = BorderStroke(1.dp, palette.outline)) {
                    Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Search, null, Modifier.size(18.dp), tint = palette.onSurfaceVariant)
                        Text("Search anything", Modifier.weight(1f).padding(start = 10.dp), style = MaterialTheme.typography.bodySmall)
                        Text("Ctrl L", style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth().heightIn(min = 58.dp, max = 220.dp)
                    .sidebarTarget(drag, "favorites", SidebarDestination(SidebarSection.FAVORITES))
                    .background(if (drag.destination == SidebarDestination(SidebarSection.FAVORITES)) palette.primary.copy(alpha = 0.12f) else Color.Transparent, RoundedCornerShape(12.dp))
                    .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.favorites.chunked(3).forEach { group ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            group.forEach { bookmark -> FavoriteTile(bookmark, Modifier.weight(1f), drag, { onOpenUrl(bookmark.url) }, { vm.removeBookmark(bookmark.id) }) }
                            repeat(3 - group.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    if (state.favorites.isEmpty()) Box(Modifier.fillMaxWidth().height(58.dp), contentAlignment = Alignment.Center) {
                        Text(if (drag.item != null) "Drop to favorite" else "Drag a tab here to favorite", style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)
                    }
                }
                LazyColumn(Modifier.weight(1f).semantics { contentDescription = "Sidebar tabs" }.onGloballyPositioned { viewport = it.boundsInRoot() }, state = list,
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    item("space-heading") {
                        Row(Modifier.fillMaxWidth().height(52.dp)
                            .sidebarTarget(drag, "pinned-header", SidebarDestination(SidebarSection.PINNED)), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { pinnedCollapsed = !pinnedCollapsed }, modifier = Modifier.size(40.dp)) {
                                Icon(if (pinnedCollapsed && drag.item == null) Icons.Outlined.ChevronRight else Icons.Outlined.ExpandMore,
                                    "Toggle pinned tabs", Modifier.size(18.dp))
                            }
                            Text((state.currentSpace?.icon ?: "◉") + "\uFE0E", color = spaceForeground)
                            Text(state.currentSpace?.name ?: "Space", Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleSmall)
                            Box {
                                ToolButton(Icons.Outlined.MoreHoriz, "Space actions") { spacesMenu = true }
                                DropdownMenu(spacesMenu, { spacesMenu = false }) {
                                    DropdownMenuItem(text = { Text("New Space") }, onClick = { createSpace = true; spacesMenu = false })
                                    DropdownMenuItem(text = { Text("Edit Space") }, onClick = { editingSpace = state.currentSpace; spacesMenu = false })
                                    DropdownMenuItem(text = { Text("Delete Space") }, enabled = state.workspace.spaces.size > 1,
                                        onClick = { deleteSpace = state.currentSpace; spacesMenu = false })
                                }
                            }
                        }
                    }
                    if (!pinnedCollapsed || drag.item != null) {
                        items(tabs.filter { it.isPinned }, key = { it.id }) { tab -> TabRow(tab, state, vm, drag,
                            tab.id in loadingIds, { vm.selectTab(tab.id); onNavigate("browser") }, { onSplitTab(tab.id) }) }
                        if (tabs.none { it.isPinned }) item("empty-pinned") {
                            Box(Modifier.fillMaxWidth().height(42.dp).sidebarTarget(drag, "empty-pinned", SidebarDestination(SidebarSection.PINNED)), contentAlignment = Alignment.CenterStart) {
                                Text(if (drag.item != null) "Drop to pin" else "Keep your everyday tabs here", Modifier.padding(start = 12.dp),
                                    style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
                            }
                        }
                    }
                    item("today-heading") {
                        Column(Modifier.sidebarTarget(drag, "today-header", SidebarDestination(SidebarSection.TODAY))) {
                            HorizontalDivider(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), color = palette.onSurface.copy(alpha = 0.12f))
                            Row(Modifier.fillMaxWidth().clickable { vm.newTab(); onNavigate("browser") }.height(48.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Add, null, Modifier.size(20.dp), tint = palette.onSurfaceVariant)
                                Text("New tab", Modifier.weight(1f).padding(start = 12.dp), color = palette.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                Text("Ctrl T", style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)
                            }
                        }
                    }
                    items(tabs.filter { !it.isPinned }, key = { it.id }) { tab -> TabRow(tab, state, vm, drag,
                        tab.id in loadingIds, { vm.selectTab(tab.id); onNavigate("browser") }, { onSplitTab(tab.id) }) }
                    item("today-tail") {
                        Box(Modifier.fillMaxWidth().height(56.dp).sidebarTarget(drag, "today-tail", SidebarDestination(SidebarSection.TODAY))) {
                            if (drag.item != null && drag.destination == SidebarDestination(SidebarSection.TODAY))
                                Text("Drop as an unpinned tab", Modifier.padding(12.dp), style = MaterialTheme.typography.labelSmall, color = palette.primary)
                        }
                    }
                    val archived = state.workspace.tabs.filter { it.spaceId == state.currentSpace?.id && it.archivedAt != null && it.closedAt == null }
                    if (archived.isNotEmpty()) item("archive") {
                        var expanded by remember { mutableStateOf(false) }
                        Column {
                            TextButton(onClick = { expanded = !expanded }) { Text("Archived · ${archived.size}", style = MaterialTheme.typography.labelSmall) }
                            if (expanded) archived.forEach { tab -> TextButton(onClick = { vm.selectTab(tab.id); onNavigate("browser") }) { Text(tab.title, maxLines = 1) } }
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(if (collapsed) 0.dp else 28.dp)) {
            if (drag.item != null && !collapsed) Text(when (drag.destination?.section) {
                SidebarSection.FAVORITES -> "Release to favorite"
                SidebarSection.PINNED -> "Release to pin"
                SidebarSection.SPACE -> if (drag.item?.favorite == true) "Release to open in Space" else "Release to move to Space"
                SidebarSection.TODAY -> "Release to place tab"
                null -> "Drag to arrange your sidebar"
            }, Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall, color = palette.primary)
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                state.workspace.spaces.forEach { space ->
                    val highlighted = drag.destination?.section == SidebarSection.SPACE && drag.destination?.id == space.id
                    val active = space.id == state.currentSpace?.id
                    val foreground = readableSpaceColor(Color(space.color), palette.onSurface,
                        listOf(sidebarTop, sidebarBottom, palette.surfaceContainerHighest, palette.primaryContainer))
                    Box(Modifier.size(48.dp).sidebarTarget(drag, "space-${space.id}", SidebarDestination(SidebarSection.SPACE, space.id))
                        .clip(RoundedCornerShape(12.dp)).background(if (highlighted) palette.primaryContainer else if (active) palette.surfaceContainerHighest else Color.Transparent)
                        .then(if (active || highlighted) Modifier.border(1.dp, if (highlighted) palette.primary else foreground, RoundedCornerShape(12.dp)) else Modifier)
                        .clickable { vm.selectSpace(space.id); onNavigate("browser") }.semantics { contentDescription = "Switch to ${space.name}"; selected = active }, contentAlignment = Alignment.Center) {
                        Text(space.icon + "\uFE0E", color = foreground, style = MaterialTheme.typography.titleMedium)
                    }
                }
                ToolButton(Icons.Outlined.Add, "New Space") { createSpace = true }
            }
            if (collapsed) {
                ToolButton(Icons.Outlined.History, "History") { onNavigate("history") }
                ToolButton(Icons.Outlined.Bookmarks, "Bookmarks") { onNavigate("bookmarks") }
                ToolButton(Icons.Outlined.Settings, "Settings") { onNavigate("settings") }
            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ToolButton(Icons.Outlined.History, "History") { onNavigate("history") }
                ToolButton(Icons.Outlined.Bookmarks, "Bookmarks") { onNavigate("bookmarks") }
                ToolButton(Icons.Outlined.Settings, "Settings") { onNavigate("settings") }
            }
        }
        drag.item?.let { item ->
            val density = LocalDensity.current
            val offset = drag.point - rootOrigin
            Surface(Modifier.offset { IntOffset(12, (offset.y - with(density) { 64.dp.toPx() }).roundToInt().coerceAtLeast(0)) }
                .widthIn(max = 320.dp).fillMaxWidth().padding(horizontal = 10.dp), shape = RoundedCornerShape(10.dp),
                color = palette.surface, shadowElevation = 8.dp) {
                Row(Modifier.height(48.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Favicon(item.favicon); Text(item.title, Modifier.weight(1f).padding(start = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    if (createSpace || editingSpace != null) {
        var name by remember(editingSpace) { mutableStateOf(editingSpace?.name ?: "") }
        var icon by remember(editingSpace) { mutableStateOf(editingSpace?.icon ?: "◉") }
        var color by remember(editingSpace) { mutableLongStateOf(editingSpace?.color ?: 0xFF426B5A) }
        AlertDialog(onDismissRequest = { createSpace = false; editingSpace = null }, title = { OrbitSystemBars(); Text(if (createSpace) "New Space" else "Edit Space") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Space name") }, singleLine = true)
                Text("Icon", style = MaterialTheme.typography.labelMedium)
                Row { listOf("◉", "▣", "✦", "☁", "♥", "△").forEach { value ->
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(if (icon == value) palette.primaryContainer else Color.Transparent)
                        .clickable { icon = value }.semantics { contentDescription = "Space icon $value" }, contentAlignment = Alignment.Center) { Text(value + "\uFE0E") }
                } }
                Text("Theme", style = MaterialTheme.typography.labelMedium)
                Row { listOf(0xFF426B5A, 0xFF6C6193, 0xFFB07D47, 0xFF477F96, 0xFF995C77, 0xFF687081).forEachIndexed { index, value ->
                    Box(Modifier.size(48.dp).padding(6.dp).clip(RoundedCornerShape(18.dp)).background(Color(value))
                        .clickable { color = value }.semantics { contentDescription = "Space theme ${index + 1}" }, contentAlignment = Alignment.Center) {
                        if (color == value) Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = Color.White)
                    }
                } }
            } },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
                if (createSpace) vm.createSpace(name, icon, color) else editingSpace?.let { vm.editSpace(it.id, name, icon, color) }
                createSpace = false; editingSpace = null
            }) { Text("Save") } }, dismissButton = { TextButton(onClick = { createSpace = false; editingSpace = null }) { Text("Cancel") } })
    }
    deleteSpace?.let { space -> AlertDialog(onDismissRequest = { deleteSpace = null }, title = { OrbitSystemBars(); Text("Delete ${space.name}?") },
        text = { Text("Its tabs will be removed. Shared favorites stay available.") }, confirmButton = { TextButton(onClick = { vm.deleteSpace(space.id); deleteSpace = null }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleteSpace = null }) { Text("Cancel") } }) }
}

@Composable
private fun FavoriteTile(bookmark: Bookmark, modifier: Modifier, drag: SidebarDragState, onOpen: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val target = drag.destination?.takeIf { it.section == SidebarSection.FAVORITES && it.id == bookmark.id }
    Box(modifier.sidebarTarget(drag, "favorite-${bookmark.id}", SidebarDestination(SidebarSection.FAVORITES, bookmark.id))) {
        Box(Modifier.fillMaxWidth().height(58.dp).alpha(if (drag.item?.id == bookmark.id) 0.35f else 1f)
            .clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .sidebarDraggable(drag, SidebarDragItem(bookmark.id, bookmark.title, bookmark.faviconUrl, true))
            .clickable(onClick = onOpen).semantics { contentDescription = "Favorite ${bookmark.title}" }, contentAlignment = Alignment.Center) {
            if (bookmark.faviconUrl == null) Text(bookmark.title.take(1), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleLarge)
            else Favicon(bookmark.faviconUrl)
            IconButton(onClick = { menu = true }, modifier = Modifier.align(Alignment.TopEnd).size(24.dp)) {
                Icon(Icons.Outlined.MoreHoriz, "Favorite actions for ${bookmark.title}", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (target != null) Box(Modifier.align(if (target.after) Alignment.CenterEnd else Alignment.CenterStart)
            .width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
        DropdownMenu(menu, { menu = false }) { DropdownMenuItem(text = { Text("Remove favorite") }, onClick = { onRemove(); menu = false }) }
    }
}

@Composable
private fun TabRow(tab: BrowserTab, state: BrowserUiState, vm: BrowserViewModel, drag: SidebarDragState,
    loading: Boolean, onSelect: () -> Unit, onSplit: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val selected = state.activeTab?.id == tab.id
    val palette = MaterialTheme.colorScheme
    val contentColor = if (selected) palette.onPrimaryContainer else palette.onSurface
    val section = if (tab.isPinned) SidebarSection.PINNED else SidebarSection.TODAY
    val target = drag.destination?.takeIf { it.section == section && it.id == tab.id && drag.item?.id != tab.id }
    val item = SidebarDragItem(tab.id, tab.title, tab.faviconUrl)
    Box(Modifier.fillMaxWidth().sidebarTarget(drag, "tab-${tab.id}", SidebarDestination(section, tab.id))) {
        Row(Modifier.fillMaxWidth().alpha(if (drag.item?.id == tab.id) 0.3f else 1f)
            .clip(RoundedCornerShape(10.dp)).background(if (selected) palette.primaryContainer else Color.Transparent)
            .then(if (selected) Modifier.border(1.dp, palette.primary, RoundedCornerShape(10.dp)) else Modifier)
            .sidebarDraggable(drag, item).clickable(onClick = onSelect)
            .height(48.dp).semantics { contentDescription = "Tab ${tab.title}"; this.selected = selected }, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).semantics { contentDescription = "Drag ${tab.title}" }
                .sidebarDraggable(drag, item, immediate = true), contentAlignment = Alignment.Center) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Favicon(tab.faviconUrl)
            }
            Text(tab.title, color = contentColor, style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Outlined.MoreHoriz, "Actions for ${tab.title}", Modifier.size(18.dp), tint = if (selected) contentColor else palette.onSurfaceVariant) }
            if (selected) IconButton(onClick = { vm.closeTab(tab.id) }, modifier = Modifier.size(40.dp)) { Icon(Icons.Outlined.Close, "Close ${tab.title}", Modifier.size(18.dp), tint = contentColor) }
        }
        if (target != null) Box(Modifier.align(if (target.after) Alignment.BottomCenter else Alignment.TopCenter)
            .fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(text = { Text(if (tab.isPinned) "Unpin tab" else "Pin tab") }, onClick = { vm.togglePin(tab.id); menu = false })
            DropdownMenuItem(text = { Text("Add to favorites") }, enabled = tab.url != "about:blank", onClick = { vm.favoriteTab(tab.id); menu = false })
            DropdownMenuItem(text = { Text("Save bookmark") }, enabled = tab.url != "about:blank", onClick = { vm.bookmark(tab, false); menu = false })
            DropdownMenuItem(text = { Text("Open in right pane") }, onClick = { onSplit(); menu = false })
            state.workspace.spaces.filter { it.id != tab.spaceId }.forEach { space ->
                DropdownMenuItem(text = { Text("Move to ${space.name}") }, onClick = { vm.moveTab(tab.id, space.id); menu = false })
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Close tab") }, onClick = { vm.closeTab(tab.id); menu = false })
        }
    }
}
