package com.takeruf.nagi.ui.settings

import androidx.compose.ui.draw.clip
import com.takeruf.nagi.ui.theme.NagiShapes
import com.takeruf.nagi.R
import com.takeruf.nagi.ui.localization.rememberNagiStrings

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import com.takeruf.nagi.ui.components.NagiIcons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.takeruf.nagi.browser.search.InputResolver
import com.takeruf.nagi.browser.search.AiSearchEngines
import com.takeruf.nagi.browser.search.CommonSearchEngines
import com.takeruf.nagi.browser.search.SearchEngineOrder
import com.takeruf.nagi.domain.model.*
import com.takeruf.nagi.ui.browser.*
import com.takeruf.nagi.ui.components.ToolButton
import com.takeruf.nagi.ui.components.NagiOverflowMenu
import com.takeruf.nagi.ui.components.NagiOverflowMenuItem
import com.takeruf.nagi.ui.theme.NagiSystemBars
import java.util.UUID

@Composable
fun SettingsScreen(state: BrowserUiState, vm: BrowserViewModel, onClearSiteData: () -> Unit = {}, updateSection: @Composable () -> Unit = {}, onBack: () -> Unit) {
    val strings = rememberNagiStrings()
    var customizingEngines by rememberSaveable { mutableStateOf(false) }
    val settingsListState = rememberLazyListState()
    BackHandler(enabled = customizingEngines) { customizingEngines = false }
    if (customizingEngines) {
        SearchEnginesScreen(state, vm, onBack = { customizingEngines = false })
        return
    }
    var clearHistory by remember { mutableStateOf(false) }
    var clearSiteData by remember { mutableStateOf(false) }
    val prefs = state.settings
    Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(start = 28.dp, top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                ToolButton(NagiIcons.ArrowBack, strings(R.string.ui_back_to_browser), onClick = onBack)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(strings(R.string.ui_settings), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        LazyColumn(Modifier.weight(1f).testTag("settings-list"), state = settingsListState, contentPadding = PaddingValues(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { SettingsSection(strings(R.string.ui_general), NagiIcons.Globe) {
            var defaultsMenu by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(strings(R.string.ui_default_search_engine), Modifier.weight(1f))
                Box {
                    TextButton(shape = NagiShapes.Rounded, onClick = { defaultsMenu = true }) {
                        Text(state.workspace.searchEngines.firstOrNull { it.id == prefs.defaultSearchEngineId }?.let { strings.engineName(it, inSettings = true) } ?: strings(R.string.ui_select))
                        Icon(NagiIcons.ChevronDown, null)
                    }
                    NagiOverflowMenu(defaultsMenu, { defaultsMenu = false }) {
                        SearchEngineOrder.sorted(state.workspace.searchEngines.filter { it.id !in AiSearchEngines.ids }, CommonSearchEngines.ids(prefs)) { it.id }
                            .forEach { engine -> NagiOverflowMenuItem(text = { Text(strings.engineName(engine, inSettings = true)) }, leadingIcon = { Icon(NagiIcons.Search, null) },
                            onClick = { vm.defaultEngine(engine.id); defaultsMenu = false }) }
                    }
                }
            }
            var aiDefaultsMenu by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(strings(R.string.ui_default_ai_engine), Modifier.weight(1f))
                Box {
                    TextButton(shape = NagiShapes.Rounded, modifier = Modifier.testTag("default-ai-engine"), onClick = { aiDefaultsMenu = true }) {
                        Text(strings.engineName(AiSearchEngines.default(prefs, state.workspace.searchEngines), inSettings = true))
                        Icon(NagiIcons.ChevronDown, null)
                    }
                    NagiOverflowMenu(aiDefaultsMenu, { aiDefaultsMenu = false }) {
                        AiSearchEngines.available(state.workspace.searchEngines).forEach { engine ->
                            NagiOverflowMenuItem(text = { Text(strings.engineName(engine, inSettings = true)) },
                                leadingIcon = { Icon(NagiIcons.Search, null) },
                                onClick = { vm.defaultAiEngine(engine.id); aiDefaultsMenu = false })
                        }
                    }
                }
            }
            SettingToggle(strings(R.string.ui_automatic_search_by_region), strings(R.string.ui_mainland_china_elsewhere_google_choosing_an_engine_turns_this_off), prefs.automaticSearchRegion, vm::automaticSearchRegion)
            if (prefs.automaticSearchRegion) Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(prefs.searchRegionCountry?.let { "$it · ${strings.translate(prefs.searchRegionSource.orEmpty())}" } ?: if (prefs.searchRegionSource == "Unavailable") strings(R.string.ui_region_unavailable_current_engine_kept) else strings(R.string.ui_detecting_region),
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(shape = NagiShapes.Rounded, onClick = vm::refreshSearchRegion) { Text(strings(R.string.ui_check_again)) }
            }
            SettingToggle(strings(R.string.ui_open_links_in_new_tab), strings(R.string.ui_page_links_you_tap_open_as_a_new_tab), prefs.openLinksInNewTab) { value -> vm.updateSettings { it.copy(openLinksInNewTab = value) } }
            SettingToggle(strings(R.string.ui_restore_tabs_on_launch), strings(R.string.ui_keep_your_spaces_and_open_tabs_between_sessions), prefs.restoreTabs) { value -> vm.updateSettings { it.copy(restoreTabs = value) } }
            SettingToggle(strings(R.string.ui_desktop_site_by_default), strings(R.string.ui_use_a_desktop_user_agent_for_newly_created_sessions), prefs.desktopDefault) { value -> vm.updateSettings { it.copy(desktopDefault = value) } }
        } }
        item { SettingsSection(strings(R.string.ui_common_search_engines), NagiIcons.Search) {
            Row(Modifier.fillMaxWidth().testTag("customize-search-engines").clip(NagiShapes.Rounded).clickable { customizingEngines = true }
                .padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(strings(R.string.ui_customize_search_engines))
                    Text(strings(R.string.ui_common_search_engines_description), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(NagiIcons.ArrowForward, null, Modifier.size(20.dp))
            }
        } }
        item { SettingsSection(strings(R.string.ui_appearance), NagiIcons.Palette) {
            FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode -> FilterChip(selected = prefs.theme == mode, onClick = { vm.updateSettings { it.copy(theme = mode) } }, label = { Text(strings(when (mode) { ThemeMode.SYSTEM -> R.string.ui_system; ThemeMode.LIGHT -> R.string.ui_light; ThemeMode.DARK -> R.string.ui_dark })) }) }
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
                TextButton(shape = NagiShapes.Rounded, onClick = { vm.restoreClosed() }) { Text(strings(R.string.ui_restore)) }
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
                TextButton(shape = NagiShapes.Rounded, enabled = prefs.archivePeriod != ArchivePeriod.NEVER, onClick = { vm.archiveNow() }) { Text(strings(R.string.ui_archive_now)) }
            }
        } }
        item { SettingsSection(strings(R.string.ui_privacy), NagiIcons.ShieldCheck) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings(R.string.ui_automatic_search_uses_api_country_is_to_resolve_the_con_012b7a40), style = MaterialTheme.typography.bodySmall)
                Text(strings(R.string.ui_your_workspace_stays_on_this_device), style = MaterialTheme.typography.titleSmall)
                Text(strings(R.string.ui_history_tabs_and_bookmarks_are_stored_locally_sites_ask_5d51ee23),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(shape = NagiShapes.Rounded, onClick = { clearHistory = true }) { Text(strings(R.string.ui_clear_browsing_history_a2d6ed)) }
                TextButton(shape = NagiShapes.Rounded, onClick = { clearSiteData = true }) { Text(strings(R.string.ui_clear_site_data)) }
                Text(strings(R.string.ui_private_browsing_and_content_blocking_are_planned_for_a_later_release), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } }
        item { updateSection() }
        item { Text("Nagi ${com.takeruf.nagi.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    if (clearSiteData) AlertDialog(onDismissRequest = { clearSiteData = false },
        title = { Text(strings(R.string.ui_clear_site_data)) },
        text = { Text(strings(R.string.ui_clear_site_data_description)) },
        confirmButton = { TextButton(onClick = { clearSiteData = false; onClearSiteData() }) { Text(strings(R.string.ui_clear_data)) } },
        dismissButton = { TextButton(onClick = { clearSiteData = false }) { Text(strings(R.string.ui_cancel)) } })
    if (clearHistory) AlertDialog(onDismissRequest = { clearHistory = false }, title = { NagiSystemBars(); Text(strings(R.string.ui_clear_browsing_history_199567)) },
        text = { Text(strings(R.string.ui_this_removes_saved_visits_from_this_device)) }, confirmButton = { TextButton(shape = NagiShapes.Rounded, onClick = { vm.clearHistory(); clearHistory = false }) { Text(strings(R.string.ui_clear_history)) } },
        dismissButton = { TextButton(shape = NagiShapes.Rounded, onClick = { clearHistory = false }) { Text(strings(R.string.ui_cancel)) } })
}

@Composable
private fun SearchEnginesScreen(state: BrowserUiState, vm: BrowserViewModel, onBack: () -> Unit) {
    val strings = rememberNagiStrings()
    val prefs = state.settings
    val commonIds = CommonSearchEngines.ids(prefs)
    val enginesById = state.workspace.searchEngines.associateBy { it.id }
    val orderedIds = SearchEngineOrder.sorted(
        state.workspace.searchEngines.map { it.id }.plus(CommonSearchEngines.CHATGPT).distinct(), commonIds
    ) { it }
    var editing by remember { mutableStateOf<SearchEngine?>(null) }
    var adding by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 28.dp, top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            ToolButton(NagiIcons.ArrowBack, strings(R.string.ui_back_to_settings), onClick = onBack)
            Column(Modifier.padding(start = 12.dp)) {
                Text(strings(R.string.ui_customize_search_engines), style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold)
                Text(strings(R.string.ui_manage_search_engines), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(Modifier.weight(1f).testTag("search-engines-list"), contentPadding = PaddingValues(32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { SettingsSection(strings(R.string.ui_common_search_engines), NagiIcons.Search) {
            Text(strings(R.string.ui_common_search_engines_description), Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            orderedIds.filter { it in commonIds || it == CommonSearchEngines.CHATGPT }.forEach { id ->
                if (id == CommonSearchEngines.CHATGPT) {
                    CommonSearchEngineToggle(id, "ChatGPT", "${AiSearchEngines.chatGpt.keyword}  ·  ${AiSearchEngines.chatGpt.urlTemplate}",
                        selected = id in commonIds, isDefault = id == AiSearchEngines.defaultId(prefs), isAi = true) { enabled ->
                        vm.updateSettings { CommonSearchEngines.select(it, id, enabled) }
                    }
                } else enginesById[id]?.let { engine ->
                    CommonSearchEngineToggle(
                        id = engine.id,
                        title = strings.engineName(engine, inSettings = true),
                        subtitle = "${engine.keyword}  ·  ${engine.urlTemplate}",
                        selected = true,
                        isDefault = engine.id in setOf(prefs.defaultSearchEngineId, AiSearchEngines.defaultId(prefs)),
                        isAi = engine.id in AiSearchEngines.ids,
                    ) { enabled -> vm.updateSettings { CommonSearchEngines.select(it, engine.id, enabled) } }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        } }
        item { SettingsSection(strings(R.string.ui_available_search_engines), NagiIcons.Search) {
            orderedIds.forEach { id ->
                val engine = if (id == CommonSearchEngines.CHATGPT) AiSearchEngines.chatGpt else enginesById[id]
                engine?.let {
                    val isAi = id in AiSearchEngines.ids
                    val isDefault = id == if (isAi) AiSearchEngines.defaultId(prefs) else prefs.defaultSearchEngineId
                    val name = strings.engineName(engine, inSettings = true)
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp).heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = id in commonIds, enabled = !isDefault,
                            onCheckedChange = { enabled -> vm.updateSettings { CommonSearchEngines.select(it, id, enabled) } },
                            modifier = Modifier.testTag("common-engine:$id").semantics {
                                contentDescription = strings(R.string.ui_include_common_engine, name)
                            })
                        Column(Modifier.weight(1f)) {
                            SearchEngineTitle(name, isAi, isDefault)
                            Text("${engine.keyword}  ·  ${engine.urlTemplate}", style = MaterialTheme.typography.bodySmall, maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(shape = NagiShapes.Rounded, enabled = !isDefault,
                            modifier = Modifier.testTag("make-default:$id").semantics {
                                contentDescription = strings(if (isAi) R.string.ui_make_ai_default_named else R.string.ui_make_1_s_default, name)
                            }, onClick = {
                                if (isAi) vm.defaultAiEngine(id) else vm.defaultEngine(id)
                            }) {
                            Text(strings(if (isAi) R.string.ui_make_ai_default else R.string.ui_make_search_default))
                        }
                        if (id != CommonSearchEngines.CHATGPT) {
                            ToolButton(NagiIcons.Pencil, strings(R.string.ui_edit_1_s, name)) { editing = engine }
                            ToolButton(NagiIcons.Trash2, strings(R.string.ui_delete_1_s_48c860, name), state.workspace.searchEngines.size > 1) { vm.deleteEngine(id) }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            TextButton(shape = NagiShapes.Rounded, onClick = { adding = true }, modifier = Modifier.padding(8.dp)) { Icon(NagiIcons.Plus, null); Text(strings(R.string.ui_add_search_engine), Modifier.padding(start = 8.dp)) }
        } }
        }
    }
    if (adding || editing != null) SearchEngineEditor(editing, state.workspace.searchEngines,
        onDismiss = { adding = false; editing = null }, onSave = { vm.saveEngine(it); adding = false; editing = null })
}

@Composable
private fun CommonSearchEngineToggle(
    id: String,
    title: String,
    subtitle: String,
    selected: Boolean,
    isDefault: Boolean = false,
    isAi: Boolean = false,
    onChange: (Boolean) -> Unit,
) {
    val strings = rememberNagiStrings()
    Row(Modifier.fillMaxWidth().testTag("selected-common-engine:$id")
        .toggleable(value = selected, enabled = !isDefault, role = Role.Switch, onValueChange = onChange)
        .padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 20.dp)) {
            SearchEngineTitle(title, isAi, isDefault)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = selected, onCheckedChange = null, enabled = !isDefault)
    }
}

@Composable
private fun SearchEngineTitle(title: String, isAi: Boolean, isDefault: Boolean) {
    val strings = rememberNagiStrings()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (isAi || isDefault) {
            Surface(shape = NagiShapes.Rounded, color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                Text(strings(when {
                    isAi && isDefault -> R.string.ui_ai_default_badge
                    isAi -> R.string.ui_ai_engine_badge
                    else -> R.string.ui_search_default_badge
                }), Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.widthIn(max = 1000.dp)) {
        Row(Modifier.padding(start = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Surface(shape = NagiShapes.Rounded, color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) { Column(content = content) }
    }
}
@Composable
private fun SettingToggle(title: String, subtitle: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(NagiShapes.Rounded).clickable { onChange(!value) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
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
    AlertDialog(onDismissRequest = onDismiss, title = { NagiSystemBars(); Text(if (engine == null) strings(R.string.ui_add_search_engine) else strings(R.string.ui_edit_1_s, strings.engineName(engine, inSettings = true))) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text(strings(R.string.ui_name)) }, singleLine = true)
            OutlinedTextField(keyword, { keyword = it }, label = { Text(strings(R.string.ui_keyword)) }, singleLine = true)
            OutlinedTextField(template, { template = it }, label = { Text(strings(R.string.ui_search_url_template)) }, supportingText = { Text(strings(R.string.ui_use_query_where_the_search_text_belongs)) })
            OutlinedTextField(icon, { icon = it }, label = { Text(strings(R.string.ui_icon_url_optional)) }, singleLine = true)
            error?.let { Text(strings.translate(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        } }, confirmButton = { TextButton(shape = NagiShapes.Rounded, onClick = {
            val value = SearchEngine(engine?.id ?: UUID.randomUUID().toString(), name.trim(), keyword.trim().lowercase(), template.trim(), icon.trim().ifBlank { null })
            error = InputResolver.validateEngine(value) ?: if (engines.any { it.id != value.id && it.keyword == value.keyword }) "That keyword is already in use" else null
            if (error == null) onSave(value)
        }) { Text(strings(R.string.ui_save)) } }, dismissButton = { TextButton(shape = NagiShapes.Rounded, onClick = onDismiss) { Text(strings(R.string.ui_cancel)) } })
}
