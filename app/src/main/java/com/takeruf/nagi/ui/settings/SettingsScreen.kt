package com.takeruf.nagi.ui.settings

import com.takeruf.nagi.R
import com.takeruf.nagi.ui.localization.rememberNagiStrings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import com.takeruf.nagi.ui.components.NagiIcons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.takeruf.nagi.browser.search.InputResolver
import com.takeruf.nagi.domain.model.*
import com.takeruf.nagi.ui.browser.*
import com.takeruf.nagi.ui.components.ToolButton
import com.takeruf.nagi.ui.theme.NagiSystemBars
import java.util.UUID

@Composable
fun SettingsScreen(state: BrowserUiState, vm: BrowserViewModel, onBack: () -> Unit) {
    val strings = rememberNagiStrings()
    var editing by remember { mutableStateOf<SearchEngine?>(null) }
    var adding by remember { mutableStateOf(false) }
    var themeColorEditor by remember { mutableStateOf(false) }
    var clearHistory by remember { mutableStateOf(false) }
    val prefs = state.settings
    Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(start = 28.dp, top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                ToolButton(NagiIcons.ArrowBack, strings(R.string.ui_back_to_browser), onClick = onBack)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(strings(R.string.ui_make_it_yours), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Text(strings(R.string.ui_a_browser_that_fits_your_workspace), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        LazyColumn(Modifier.weight(1f).testTag("settings-list"), contentPadding = PaddingValues(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { SettingsSection(strings(R.string.ui_general), NagiIcons.Globe) {
            var defaultsMenu by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(strings(R.string.ui_default_search_engine), Modifier.weight(1f))
                Box {
                    TextButton(onClick = { defaultsMenu = true }) {
                        Text(state.workspace.searchEngines.firstOrNull { it.id == prefs.defaultSearchEngineId }?.name ?: strings(R.string.ui_select))
                        Icon(NagiIcons.ChevronDown, null)
                    }
                    DropdownMenu(defaultsMenu, { defaultsMenu = false }) {
                        state.workspace.searchEngines.forEach { engine -> DropdownMenuItem(text = { Text(engine.name) },
                            onClick = { vm.defaultEngine(engine.id); defaultsMenu = false }) }
                    }
                }
            }
            SettingToggle(strings(R.string.ui_automatic_search_by_region), strings(R.string.ui_mainland_china_elsewhere_google_choosing_an_engine_turns_this_off), prefs.automaticSearchRegion, vm::automaticSearchRegion)
            if (prefs.automaticSearchRegion) Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(prefs.searchRegionCountry?.let { "$it · ${strings.translate(prefs.searchRegionSource.orEmpty())}" } ?: if (prefs.searchRegionSource == "Unavailable") strings(R.string.ui_region_unavailable_current_engine_kept) else strings(R.string.ui_detecting_region),
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = vm::refreshSearchRegion) { Text(strings(R.string.ui_check_again)) }
            }
            SettingToggle(strings(R.string.ui_open_links_in_new_tab), strings(R.string.ui_page_links_you_tap_open_as_a_new_tab), prefs.openLinksInNewTab) { value -> vm.updateSettings { it.copy(openLinksInNewTab = value) } }
            SettingToggle(strings(R.string.ui_restore_tabs_on_launch), strings(R.string.ui_keep_your_spaces_and_open_tabs_between_sessions), prefs.restoreTabs) { value -> vm.updateSettings { it.copy(restoreTabs = value) } }
            SettingToggle(strings(R.string.ui_desktop_site_by_default), strings(R.string.ui_use_a_desktop_user_agent_for_newly_created_sessions), prefs.desktopDefault) { value -> vm.updateSettings { it.copy(desktopDefault = value) } }
        } }
        item { SettingsSection(strings(R.string.ui_search_engines), NagiIcons.Search) {
            state.workspace.searchEngines.forEach { engine ->
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp).heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(engine.name, style = MaterialTheme.typography.titleSmall)
                            if (engine.id == prefs.defaultSearchEngineId) Text(strings(R.string.ui_default), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                        }
                        Text("${engine.keyword}  ·  ${engine.urlTemplate}", style = MaterialTheme.typography.bodySmall, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ToolButton(NagiIcons.CircleCheck, strings(R.string.ui_make_1_s_default, engine.name), engine.id != prefs.defaultSearchEngineId) { vm.defaultEngine(engine.id) }
                    ToolButton(NagiIcons.Pencil, strings(R.string.ui_edit_1_s, engine.name)) { editing = engine }
                    ToolButton(NagiIcons.Trash2, strings(R.string.ui_delete_1_s_48c860, engine.name), state.workspace.searchEngines.size > 1) { vm.deleteEngine(engine.id) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            TextButton(onClick = { adding = true }, modifier = Modifier.padding(8.dp)) { Icon(NagiIcons.Plus, null); Text(strings(R.string.ui_add_search_engine), Modifier.padding(start = 8.dp)) }
        } }
        item { SettingsSection(strings(R.string.ui_appearance), NagiIcons.Palette) {
            FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode -> FilterChip(selected = prefs.theme == mode, onClick = { vm.updateSettings { it.copy(theme = mode) } }, label = { Text(strings(when (mode) { ThemeMode.SYSTEM -> R.string.ui_system; ThemeMode.LIGHT -> R.string.ui_light; ThemeMode.DARK -> R.string.ui_dark })) }) }
            }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings(R.string.ui_theme_color))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(strings(R.string.ui_forest) to 0xFF426B5A, strings(R.string.ui_blue) to 0xFF3568C0, strings(R.string.ui_purple) to 0xFF8059B1,
                        strings(R.string.ui_rose) to 0xFFB4496B, strings(R.string.ui_orange) to 0xFFC76D26, strings(R.string.ui_slate) to 0xFF64748B).forEach { (name, color) ->
                        FilterChip(selected = prefs.themeColor == color,
                            onClick = { vm.updateSettings { it.copy(themeColor = color) } },
                            label = { Text(name) }, leadingIcon = {
                                Surface(Modifier.size(18.dp), shape = CircleShape, color = Color(color),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {}
                            })
                    }
                }
                TextButton(onClick = { themeColorEditor = true }) {
                    Icon(NagiIcons.Palette, null, Modifier.size(18.dp))
                    Text(strings(R.string.ui_custom_color_1_s, "%06X".format(prefs.themeColor and 0xFFFFFF)), Modifier.padding(start = 8.dp))
                }
            }
            var width by remember(prefs.sidebarWidth) { mutableFloatStateOf(prefs.sidebarWidth) }
            Column(Modifier.padding(16.dp)) {
                Row { Text(strings(R.string.ui_sidebar_width), Modifier.weight(1f)); Text(if (prefs.sidebarCollapsed) strings(R.string.ui_collapsed) else "${width.toInt()} dp", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Slider(value = width, onValueChange = { width = it }, valueRange = 220f..380f,
                    onValueChangeFinished = {
                        val selectedWidth = width
                        vm.updateSettings { it.copy(sidebarWidth = if (selectedWidth <= 220f) it.sidebarWidth else selectedWidth,
                            sidebarCollapsed = selectedWidth <= 220f) }
                        if (selectedWidth <= 220f) width = prefs.sidebarWidth
                    })
                Text(strings(R.string.ui_at_the_minimum_width_the_sidebar_folds_into_a_rail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(strings(R.string.ui_you_can_also_drag_the_sidebar_edge), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } }
        item { SettingsSection(strings(R.string.ui_tabs), NagiIcons.PanelsTopLeft) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(strings(R.string.ui_restore_a_closed_tab))
                    Text("Ctrl Shift T", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { vm.restoreClosed() }) { Text(strings(R.string.ui_restore)) }
            }
            Column(Modifier.padding(16.dp)) {
                Text(strings(R.string.ui_archive_inactive_tabs))
                Text(strings(R.string.ui_checked_on_launch_or_when_you_choose_archive_now_pinned_b64b52e0),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ArchivePeriod.entries.forEach { period -> FilterChip(prefs.archivePeriod == period,
                        onClick = { vm.updateSettings { it.copy(archivePeriod = period) } }, label = { Text(when (period) {
                            ArchivePeriod.NEVER -> strings(R.string.ui_never); ArchivePeriod.DAY -> strings(R.string.ui_24_hours); ArchivePeriod.WEEK -> strings(R.string.ui_7_days); ArchivePeriod.MONTH -> strings(R.string.ui_30_days)
                        }) }) }
                }
                TextButton(enabled = prefs.archivePeriod != ArchivePeriod.NEVER, onClick = { vm.archiveNow() }) { Text(strings(R.string.ui_archive_now)) }
            }
        } }
        item { SettingsSection(strings(R.string.ui_privacy), NagiIcons.ShieldCheck) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings(R.string.ui_automatic_search_uses_api_country_is_to_resolve_the_con_012b7a40), style = MaterialTheme.typography.bodySmall)
                Text(strings(R.string.ui_your_workspace_stays_on_this_device), style = MaterialTheme.typography.titleSmall)
                Text(strings(R.string.ui_history_tabs_and_bookmarks_are_stored_locally_sites_ask_5d51ee23),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { clearHistory = true }) { Text(strings(R.string.ui_clear_browsing_history_a2d6ed)) }
                Text(strings(R.string.ui_private_browsing_and_content_blocking_are_planned_for_a_later_release), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } }
        item { Text(strings(R.string.ui_nagi_0_1_0_made_for_a_little_more_room), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    if (themeColorEditor) ThemeColorEditor(prefs.themeColor, onDismiss = { themeColorEditor = false }) { color ->
        vm.updateSettings { it.copy(themeColor = color) }; themeColorEditor = false
    }
    if (adding || editing != null) SearchEngineEditor(editing, state.workspace.searchEngines,
        onDismiss = { adding = false; editing = null }, onSave = { vm.saveEngine(it); adding = false; editing = null })
    if (clearHistory) AlertDialog(onDismissRequest = { clearHistory = false }, title = { NagiSystemBars(); Text(strings(R.string.ui_clear_browsing_history_199567)) },
        text = { Text(strings(R.string.ui_this_removes_saved_visits_from_this_device)) }, confirmButton = { TextButton(onClick = { vm.clearHistory(); clearHistory = false }) { Text(strings(R.string.ui_clear_history)) } },
        dismissButton = { TextButton(onClick = { clearHistory = false }) { Text(strings(R.string.ui_cancel)) } })
}

@Composable
private fun SettingsSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.widthIn(max = 1000.dp)) {
        Row(Modifier.padding(start = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
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
    val strings = rememberNagiStrings()
    var name by remember { mutableStateOf(engine?.name ?: "") }
    var keyword by remember { mutableStateOf(engine?.keyword ?: "") }
    var template by remember { mutableStateOf(engine?.urlTemplate ?: "https://example.com/search?q={query}") }
    var icon by remember { mutableStateOf(engine?.iconUrl ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { NagiSystemBars(); Text(if (engine == null) strings(R.string.ui_add_search_engine) else strings(R.string.ui_edit_search_engine)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text(strings(R.string.ui_name)) }, singleLine = true)
            OutlinedTextField(keyword, { keyword = it }, label = { Text(strings(R.string.ui_keyword)) }, singleLine = true)
            OutlinedTextField(template, { template = it }, label = { Text(strings(R.string.ui_search_url_template)) }, supportingText = { Text(strings(R.string.ui_use_query_where_the_search_text_belongs)) })
            OutlinedTextField(icon, { icon = it }, label = { Text(strings(R.string.ui_icon_url_optional)) }, singleLine = true)
            error?.let { Text(strings.translate(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        } }, confirmButton = { TextButton(onClick = {
            val value = SearchEngine(engine?.id ?: UUID.randomUUID().toString(), name.trim(), keyword.trim().lowercase(), template.trim(), icon.trim().ifBlank { null })
            error = InputResolver.validateEngine(value) ?: if (engines.any { it.id != value.id && it.keyword == value.keyword }) "That keyword is already in use" else null
            if (error == null) onSave(value)
        }) { Text(strings(R.string.ui_save)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(strings(R.string.ui_cancel)) } })
}

@Composable
private fun ThemeColorEditor(current: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    val strings = rememberNagiStrings()
    var hex by remember { mutableStateOf("%06X".format(current and 0xFFFFFF)) }
    val valid = hex.removePrefix("#").matches(Regex("[0-9a-fA-F]{6}"))
    AlertDialog(onDismissRequest = onDismiss, title = { NagiSystemBars(); Text(strings(R.string.ui_custom_theme_color)) },
        text = {
            OutlinedTextField(hex, { hex = it }, label = { Text(strings(R.string.ui_hex_color)) }, singleLine = true,
                isError = !valid, supportingText = { Text(strings(R.string.ui_six_hexadecimal_digits_for_example_3568c0)) },
                leadingIcon = {
                    if (valid) Surface(Modifier.size(24.dp), shape = CircleShape,
                        color = Color(0xFF000000 or hex.removePrefix("#").toLong(16)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {}
                })
        }, confirmButton = { TextButton(enabled = valid, onClick = {
            onSave(0xFF000000 or hex.removePrefix("#").toLong(16))
        }) { Text(strings(R.string.ui_save)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(strings(R.string.ui_cancel)) } })
}
