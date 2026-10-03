package com.orbit.browser.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orbit.browser.browser.search.InputResolver
import com.orbit.browser.domain.model.*
import com.orbit.browser.ui.browser.*
import com.orbit.browser.ui.components.ToolButton
import com.orbit.browser.ui.theme.OrbitSystemBars
import java.util.UUID

@Composable
fun SettingsScreen(state: BrowserUiState, vm: BrowserViewModel, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<SearchEngine?>(null) }
    var adding by remember { mutableStateOf(false) }
    var clearHistory by remember { mutableStateOf(false) }
    val prefs = state.settings
    Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(start = 28.dp, top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                ToolButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back to browser", onClick = onBack)
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Make it yours", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Text("A browser that fits your workspace.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        LazyColumn(Modifier.weight(1f).testTag("settings-list"), contentPadding = PaddingValues(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { SettingsSection("General") {
            var defaultsMenu by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Default search engine", Modifier.weight(1f))
                Box {
                    TextButton(onClick = { defaultsMenu = true }) {
                        Text(state.workspace.searchEngines.firstOrNull { it.id == prefs.defaultSearchEngineId }?.name ?: "Select")
                        Icon(Icons.Outlined.ExpandMore, null)
                    }
                    DropdownMenu(defaultsMenu, { defaultsMenu = false }) {
                        state.workspace.searchEngines.forEach { engine -> DropdownMenuItem(text = { Text(engine.name) },
                            onClick = { vm.defaultEngine(engine.id); defaultsMenu = false }) }
                    }
                }
            }
            SettingToggle("Automatic search by region", "Mainland China: 百度 · elsewhere: Google. Choosing an engine turns this off.", prefs.automaticSearchRegion, vm::automaticSearchRegion)
            if (prefs.automaticSearchRegion) Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(prefs.searchRegionCountry?.let { "$it · ${prefs.searchRegionSource}" } ?: if (prefs.searchRegionSource == "Unavailable") "Region unavailable · current engine kept" else "Detecting region…",
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = vm::refreshSearchRegion) { Text("Check again") }
            }
            SettingToggle("Open links in new tab", "Page links you tap open as a new tab", prefs.openLinksInNewTab) { value -> vm.updateSettings { it.copy(openLinksInNewTab = value) } }
            SettingToggle("Restore tabs on launch", "Keep your Spaces and open tabs between sessions", prefs.restoreTabs) { value -> vm.updateSettings { it.copy(restoreTabs = value) } }
            SettingToggle("Desktop site by default", "Use a desktop user agent for newly created sessions", prefs.desktopDefault) { value -> vm.updateSettings { it.copy(desktopDefault = value) } }
        } }
        item { SettingsSection("Search engines") {
            state.workspace.searchEngines.forEach { engine ->
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp).heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(engine.name, style = MaterialTheme.typography.titleSmall)
                            if (engine.id == prefs.defaultSearchEngineId) Text("  Default", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                        }
                        Text("${engine.keyword}  ·  ${engine.urlTemplate}", style = MaterialTheme.typography.bodySmall, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ToolButton(Icons.Outlined.CheckCircle, "Make ${engine.name} default", engine.id != prefs.defaultSearchEngineId) { vm.defaultEngine(engine.id) }
                    ToolButton(Icons.Outlined.Edit, "Edit ${engine.name}") { editing = engine }
                    ToolButton(Icons.Outlined.DeleteOutline, "Delete ${engine.name}", state.workspace.searchEngines.size > 1) { vm.deleteEngine(engine.id) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            TextButton(onClick = { adding = true }, modifier = Modifier.padding(8.dp)) { Icon(Icons.Outlined.Add, null); Text("Add search engine", Modifier.padding(start = 8.dp)) }
        } }
        item { SettingsSection("Appearance") {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode -> FilterChip(selected = prefs.theme == mode, onClick = { vm.updateSettings { it.copy(theme = mode) } }, label = { Text(mode.name.lowercase().replaceFirstChar(Char::uppercase)) }) }
            }
            var width by remember(prefs.sidebarWidth) { mutableFloatStateOf(prefs.sidebarWidth) }
            Column(Modifier.padding(16.dp)) {
                Row { Text("Sidebar width", Modifier.weight(1f)); Text("${width.toInt()} dp", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Slider(value = width, onValueChange = { width = it }, valueRange = 220f..380f,
                    onValueChangeFinished = { vm.updateSettings { it.copy(sidebarWidth = width) } })
                Text("You can also drag the sidebar edge.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } }
        item { SettingsSection("Tabs") {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Restore a closed tab")
                    Text("Ctrl Shift T", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { vm.restoreClosed() }) { Text("Restore") }
            }
            Column(Modifier.padding(16.dp)) {
                Text("Archive inactive tabs")
                Text("Checked on launch or when you choose Archive now. Pinned and selected tabs are kept.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ArchivePeriod.entries.forEach { period -> FilterChip(prefs.archivePeriod == period,
                        onClick = { vm.updateSettings { it.copy(archivePeriod = period) } }, label = { Text(when (period) {
                            ArchivePeriod.NEVER -> "Never"; ArchivePeriod.DAY -> "24 hours"; ArchivePeriod.WEEK -> "7 days"; ArchivePeriod.MONTH -> "30 days"
                        }) }) }
                }
                TextButton(enabled = prefs.archivePeriod != ArchivePeriod.NEVER, onClick = { vm.archiveNow() }) { Text("Archive now") }
            }
        } }
        item { SettingsSection("Privacy") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Automatic search uses api.country.is to resolve the connection country. It sees your IP, but receives no search or browsing history. If unavailable, mobile network, SIM, then device region are used. A VPN can change the detected country. Turn off Automatic search by region to stop lookups.", style = MaterialTheme.typography.bodySmall)
                Text("Your workspace stays on this device.", style = MaterialTheme.typography.titleSmall)
                Text("History, tabs and bookmarks are stored locally. Sites ask before accessing your camera, microphone or location. Third-party cookies are blocked.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { clearHistory = true }) { Text("Clear browsing history") }
                Text("Private browsing and content blocking are planned for a later release.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } }
        item { Text("Orbit 0.1.0  ·  Made for a little more room.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    if (adding || editing != null) SearchEngineEditor(editing, state.workspace.searchEngines,
        onDismiss = { adding = false; editing = null }, onSave = { vm.saveEngine(it); adding = false; editing = null })
    if (clearHistory) AlertDialog(onDismissRequest = { clearHistory = false }, title = { OrbitSystemBars(); Text("Clear browsing history?") },
        text = { Text("This removes saved visits from this device.") }, confirmButton = { TextButton(onClick = { vm.clearHistory(); clearHistory = false }) { Text("Clear history") } },
        dismissButton = { TextButton(onClick = { clearHistory = false }) { Text("Cancel") } })
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.widthIn(max = 1000.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) { Column(content = content) }
    }
}
@Composable
private fun SettingToggle(title: String, subtitle: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 20.dp)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun SearchEngineEditor(engine: SearchEngine?, engines: List<SearchEngine>, onDismiss: () -> Unit, onSave: (SearchEngine) -> Unit) {
    var name by remember { mutableStateOf(engine?.name ?: "") }
    var keyword by remember { mutableStateOf(engine?.keyword ?: "") }
    var template by remember { mutableStateOf(engine?.urlTemplate ?: "https://example.com/search?q={query}") }
    var icon by remember { mutableStateOf(engine?.iconUrl ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { OrbitSystemBars(); Text(if (engine == null) "Add search engine" else "Edit search engine") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
            OutlinedTextField(keyword, { keyword = it }, label = { Text("Keyword") }, singleLine = true)
            OutlinedTextField(template, { template = it }, label = { Text("Search URL Template") }, supportingText = { Text("Use {query} where the search text belongs") })
            OutlinedTextField(icon, { icon = it }, label = { Text("Icon URL (optional)") }, singleLine = true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        } }, confirmButton = { TextButton(onClick = {
            val value = SearchEngine(engine?.id ?: UUID.randomUUID().toString(), name.trim(), keyword.trim().lowercase(), template.trim(), icon.trim().ifBlank { null })
            error = InputResolver.validateEngine(value) ?: if (engines.any { it.id != value.id && it.keyword == value.keyword }) "That keyword is already in use" else null
            if (error == null) onSave(value)
        }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
