package com.takeruf.nagi.ui.settings

import kotlin.math.roundToInt
import androidx.compose.ui.draw.clip
import com.takeruf.nagi.ui.theme.NagiShapes
import com.takeruf.nagi.R
import com.takeruf.nagi.ui.localization.rememberNagiStrings

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import com.takeruf.nagi.ui.components.NagiIcons
import androidx.compose.runtime.*
import androidx.lifecycle.compose.LifecycleResumeEffect
import android.view.autofill.AutofillManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
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

internal enum class SettingsCategory(val title: Int) {
    GENERAL(R.string.ui_general), APPEARANCE(R.string.ui_appearance), SEARCH(R.string.ui_search_engines),
    TABS(R.string.ui_tabs), VIDEO(R.string.ui_video), PRIVACY(R.string.ui_privacy),
    AUTOFILL(R.string.ui_password_autofill), ABOUT(R.string.ui_about_nagi),
}

@Composable
private fun SettingsCategoryRow(category: SettingsCategory, selected: Boolean, compact: Boolean, onClick: () -> Unit) {
    val strings = rememberNagiStrings()
    val icon = when (category) {
        SettingsCategory.GENERAL -> NagiIcons.SlidersHorizontal
        SettingsCategory.APPEARANCE -> NagiIcons.Palette
        SettingsCategory.SEARCH -> NagiIcons.Search
        SettingsCategory.TABS -> NagiIcons.PanelsTopLeft
        SettingsCategory.VIDEO -> NagiIcons.Monitor
        SettingsCategory.PRIVACY -> NagiIcons.ShieldCheck
        SettingsCategory.AUTOFILL -> NagiIcons.KeyRound
        SettingsCategory.ABOUT -> NagiIcons.Info
    }
    Row(Modifier.fillMaxWidth().testTag("settings-category-${category.name.lowercase()}").clip(NagiShapes.Rounded)
        .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
        .selectable(selected = selected, role = if (compact) Role.Button else Role.Tab, onClick = onClick)
        .heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, null, Modifier.size(22.dp), tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(strings(category.title), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        if (compact) Icon(NagiIcons.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsAction(title: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().clip(NagiShapes.Rounded).clickable(onClick = onClick)
        .heightIn(min = 60.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        Icon(NagiIcons.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SettingsScreen(state: BrowserUiState, vm: BrowserViewModel, onClearSiteData: () -> Unit = {}, updateSection: @Composable () -> Unit = {}, onSuspendTabs: () -> Unit = {}, displayRotation: Int = 0, onBack: () -> Unit) {
    val strings = rememberNagiStrings()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val autofillApproved = com.takeruf.nagi.browser.engine.PasswordAutofillAvailability.approved
    val autofillManager = remember(context) { if (autofillApproved) context.getSystemService(AutofillManager::class.java) else null }
    var autofillEnabled by remember { mutableStateOf(autofillManager?.isEnabled == true) }
    LifecycleResumeEffect(autofillManager) {
        autofillEnabled = autofillManager?.isEnabled == true
        onPauseOrDispose { }
    }
    var customizingEngines by rememberSaveable { mutableStateOf(false) }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = customizingEngines) { customizingEngines = false }
    if (customizingEngines) {
        SearchEnginesScreen(state, vm, onBack = { customizingEngines = false })
        return
    }
    var customizingNewTab by remember { mutableStateOf(false) }
    if (customizingNewTab) NewTabAppearanceDialog(state.settings, vm) { customizingNewTab = false }
    var clearHistory by remember { mutableStateOf(false) }
    var clearSiteData by remember { mutableStateOf(false) }
    val prefs = state.settings
    var pendingSpacesEnabled by rememberSaveable { mutableStateOf<Boolean?>(null) }
    pendingSpacesEnabled?.let { enabled ->
        AlertDialog(
            onDismissRequest = { pendingSpacesEnabled = null },
            modifier = Modifier.testTag("spaces-confirmation"),
            title = { NagiSystemBars(); Text(strings(if (enabled) R.string.ui_enable_spaces_title else R.string.ui_disable_spaces_title)) },
            text = { Text(strings(if (enabled) R.string.ui_enable_spaces_message else R.string.ui_disable_spaces_message),
                Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = {
                TextButton(shape = NagiShapes.Rounded, modifier = Modifier.testTag("confirm-spaces-change"), onClick = {
                    vm.updateSettings { it.copy(spacesEnabled = enabled) }
                    pendingSpacesEnabled = null
                }) { Text(strings(if (enabled) R.string.ui_enable_spaces else R.string.ui_disable_spaces)) }
            },
            dismissButton = {
                TextButton(shape = NagiShapes.Rounded, onClick = { pendingSpacesEnabled = null }) { Text(strings(R.string.ui_cancel)) }
            },
        )
    }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("settings-screen")) {
        val wide = maxWidth >= 760.dp
        val category = selectedCategory?.let { SettingsCategory.valueOf(it) } ?: SettingsCategory.GENERAL
        val categories = SettingsCategory.entries.filter { it != SettingsCategory.AUTOFILL || autofillApproved }
        LaunchedEffect(wide) { if (wide && selectedCategory == null) selectedCategory = SettingsCategory.GENERAL.name }
        BackHandler(enabled = !wide && selectedCategory != null) { selectedCategory = null }
        val showCategories = !wide && selectedCategory == null
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = if (wide) 24.dp else 12.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                ToolButton(NagiIcons.ArrowBack, strings(if (wide || showCategories) R.string.ui_back_to_browser else R.string.ui_back_to_settings),
                    onClick = { if (wide || showCategories) onBack() else selectedCategory = null })
                Text(strings(if (wide || showCategories) R.string.ui_settings else category.title), Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (wide || showCategories) {
                    LazyColumn(Modifier.then(if (wide) Modifier.width(236.dp) else Modifier.fillMaxWidth()).fillMaxHeight()
                        .testTag("settings-categories"), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        categories.forEach { destination -> item(key = destination.name) {
                            SettingsCategoryRow(destination, selected = wide && category == destination, compact = !wide) {
                                selectedCategory = destination.name
                            }
                        } }
                    }
                }
                if (wide) VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                if (!showCategories) Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (wide) Text(strings(category.title), Modifier.padding(start = 32.dp, top = 8.dp, bottom = 20.dp),
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    key(category) {
                        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("settings-list"),
                            contentPadding = PaddingValues(start = if (wide) 32.dp else 16.dp, end = if (wide) 32.dp else 16.dp, bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (category == SettingsCategory.GENERAL) item { SettingsSection(strings(R.string.ui_general), NagiIcons.Globe, showHeading = false) {
                                SettingToggle(strings(R.string.ui_spaces_enabled), null, prefs.spacesEnabled) { value ->
                                    pendingSpacesEnabled = value
                                }
                                SettingToggle(strings(R.string.ui_open_links_in_new_tab), null, prefs.openLinksInNewTab) { value -> vm.updateSettings { it.copy(openLinksInNewTab = value) } }
                                SettingToggle(strings(R.string.ui_block_external_apps), null, prefs.blockExternalApps) { value -> vm.updateSettings { it.copy(blockExternalApps = value) } }
                                SettingToggle(strings(R.string.ui_page_drag), null, prefs.nativePageDrag) { value -> vm.updateSettings { it.copy(nativePageDrag = value) } }
                                SettingToggle(strings(R.string.ui_restore_tabs_on_launch), null, prefs.restoreTabs) { value -> vm.updateSettings { it.copy(restoreTabs = value) } }
                                SettingToggle(strings(R.string.ui_desktop_site_by_default), null, prefs.desktopDefault) { value -> vm.updateSettings { it.copy(desktopDefault = value) } }
                            } }
                            if (category == SettingsCategory.VIDEO) item { SettingsSection(strings(R.string.ui_video), NagiIcons.Monitor, showHeading = false) {
                                SettingToggle(strings(R.string.ui_video_auto_pip), strings(R.string.ui_video_auto_pip_condition), prefs.autoVideoPip) { value -> vm.updateSettings { it.copy(autoVideoPip = value) } }
                                SettingToggle(strings(R.string.ui_video_popups), strings(R.string.ui_video_popups_condition), prefs.videoPopups) { value -> vm.updateSettings { it.copy(videoPopups = value) } }
                            } }
                            if (category == SettingsCategory.SEARCH) item { SettingsSection(strings(R.string.ui_search_engines), NagiIcons.Search, showHeading = false) {
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
                                SettingToggle(strings(R.string.ui_automatic_search_by_region), strings(R.string.ui_mainland_china_elsewhere_google_choosing_an_engine_turns_this_off), prefs.automaticSearchRegion, onChange = vm::automaticSearchRegion)
                                if (prefs.automaticSearchRegion) Text(strings(R.string.ui_automatic_search_uses_api_country_is_to_resolve_the_con_012b7a40), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (prefs.automaticSearchRegion) Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(prefs.searchRegionCountry?.let { "$it · ${strings.translate(prefs.searchRegionSource.orEmpty())}" } ?: if (prefs.searchRegionSource == "Unavailable") strings(R.string.ui_region_unavailable_current_engine_kept) else strings(R.string.ui_detecting_region),
                                        Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    TextButton(shape = NagiShapes.Rounded, onClick = vm::refreshSearchRegion) { Text(strings(R.string.ui_check_again)) }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                SettingsAction(strings(R.string.ui_customize_search_engines), Modifier.testTag("customize-search-engines")) { customizingEngines = true }
                            } }
                            if (category == SettingsCategory.APPEARANCE) item { SettingsSection(strings(R.string.ui_appearance), NagiIcons.Palette, showHeading = false) {
                                SettingToggle(strings(R.string.ui_bottom_search_bar), null, prefs.bottomSearchBar) { value ->
                                    vm.updateSettings { it.copy(bottomSearchBar = value) }
                                }
                                SettingToggle(strings(R.string.ui_avoid_camera_overlap), strings(R.string.ui_avoid_camera_overlap_description), prefs.avoidCameraOverlap) { value ->
                                    vm.updateSettings { it.copy(avoidCameraOverlap = value) }
                                }
                                if (prefs.avoidCameraOverlap) {
                                    val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
                                        android.content.res.Configuration.ORIENTATION_LANDSCAPE
                                    CameraPositionSelector(prefs.cameraLeftRotation, displayRotation, landscape) { leftRotation ->
                                        vm.updateSettings { it.copy(cameraLeftRotation = leftRotation) }
                                    }
                                }
                                if (prefs.avoidCameraOverlap && prefs.cameraLeftRotation != null) {
                                    val margin = prefs.cameraSideMargin.toFloat()
                                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("settings-camera-margin")) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(strings(R.string.ui_camera_side_margin), Modifier.weight(1f))
                                            TextButton(shape = NagiShapes.Rounded,
                                                enabled = prefs.cameraSideMargin != 32,
                                                onClick = { vm.updateSettings { it.copy(cameraSideMargin = 32) } }) {
                                                Text(strings(R.string.ui_camera_margin_reset))
                                            }
                                        }
                                        Text(strings(R.string.ui_camera_margin_hint), style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically) {
                                            TextButton(shape = NagiShapes.Rounded, enabled = margin > 0,
                                                modifier = Modifier.semantics { contentDescription = strings(R.string.ui_camera_margin_decrease) },
                                                onClick = { vm.updateSettings { it.copy(cameraSideMargin = (it.cameraSideMargin - 1).coerceAtLeast(0)) } }) { Text("−") }
                                            Text("${prefs.cameraSideMargin} dp", Modifier.padding(horizontal = 16.dp))
                                            TextButton(shape = NagiShapes.Rounded, enabled = margin < 64,
                                                modifier = Modifier.semantics { contentDescription = strings(R.string.ui_camera_margin_increase) },
                                                onClick = { vm.updateSettings { it.copy(cameraSideMargin = (it.cameraSideMargin + 1).coerceAtMost(64)) } }) { Text("+") }
                                        }
                                        Slider(value = margin, onValueChange = { value ->
                                            vm.updateSettings { it.copy(cameraSideMargin = value.roundToInt()) }
                                        }, valueRange = 0f..64f,
                                            modifier = Modifier.testTag("settings-camera-margin-slider"))
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(strings(R.string.ui_camera_margin_narrow), style = MaterialTheme.typography.labelSmall)
                                            Text(strings(R.string.ui_camera_margin_wide), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                                Row(Modifier.fillMaxWidth().testTag("settings-new-tab-background").clip(NagiShapes.Rounded)
                                    .clickable { customizingNewTab = true }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(strings(R.string.ui_new_tab_background), Modifier.weight(1f))
                                    Icon(NagiIcons.ChevronRight, null, Modifier.size(20.dp))
                                }
                                Text(strings(R.string.ui_theme), Modifier.padding(start = 16.dp, top = 16.dp), style = MaterialTheme.typography.titleSmall)
                                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                }
                            } }
                            if (category == SettingsCategory.TABS) item { SettingsSection(strings(R.string.ui_tabs), NagiIcons.PanelsTopLeft, showHeading = false) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(strings(R.string.ui_restore_a_closed_tab))
                                        Text("Ctrl Shift T", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    TextButton(shape = NagiShapes.Rounded, onClick = { vm.restoreClosed() }) { Text(strings(R.string.ui_restore)) }
                                }
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(strings(R.string.ui_sleep_tabs_description), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    TextButton(shape = NagiShapes.Rounded, onClick = onSuspendTabs, modifier = Modifier.testTag("suspend-background-tabs")) {
                                        Text(strings(R.string.ui_sleep_tabs))
                                    }
                                }
                                Column(Modifier.padding(16.dp)) {
                                    Text(strings(R.string.ui_archive_inactive_tabs))
                                    Text(strings(R.string.ui_checked_on_launch_or_when_you_choose_archive_now_pinned_b64b52e0),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ArchivePeriod.entries.forEach { period -> FilterChip(prefs.archivePeriod == period,
                                            onClick = { vm.updateSettings { it.copy(archivePeriod = period) } }, label = { Text(when (period) {
                                                ArchivePeriod.NEVER -> strings(R.string.ui_never); ArchivePeriod.DAY -> strings(R.string.ui_24_hours); ArchivePeriod.WEEK -> strings(R.string.ui_7_days); ArchivePeriod.MONTH -> strings(R.string.ui_30_days)
                                            }) }) }
                                    }
                                    TextButton(shape = NagiShapes.Rounded, enabled = prefs.archivePeriod != ArchivePeriod.NEVER, onClick = { vm.archiveNow() }) { Text(strings(R.string.ui_archive_now)) }
                                }
                            } }
                            if (category == SettingsCategory.AUTOFILL && autofillApproved) item { SettingsSection(strings(R.string.ui_password_autofill), NagiIcons.KeyRound, showHeading = false) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(strings(if (autofillManager?.isAutofillSupported != true) R.string.ui_autofill_unsupported
                                        else if (autofillEnabled) R.string.ui_autofill_enabled else R.string.ui_autofill_disabled),
                                        modifier = Modifier.testTag("autofill-status"), style = MaterialTheme.typography.titleSmall)
                                    Text(strings(R.string.ui_password_autofill_description),
                                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    TextButton(shape = NagiShapes.Rounded, modifier = Modifier.testTag("autofill-settings"), onClick = {
                                        val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE,
                                            Uri.parse("package:${context.packageName}"))
                                        try {
                                            context.startActivity(intent)
                                        } catch (_: ActivityNotFoundException) {
                                            try {
                                                context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                            } catch (_: ActivityNotFoundException) {
                                                Toast.makeText(context, strings(R.string.ui_autofill_settings_unavailable), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }) { Text(strings(R.string.ui_open_autofill_settings)) }
                                }
                            } }
                            if (category == SettingsCategory.PRIVACY) item { SettingsSection(strings(R.string.ui_privacy), NagiIcons.ShieldCheck, showHeading = false) {
                                SettingToggle(strings(R.string.ui_ad_blocking), null, prefs.adBlockingEnabled, modifier = Modifier.testTag("ad-blocking-switch")) { enabled ->
                                    vm.updateSettings { it.copy(adBlockingEnabled = enabled) }
                                }
                                SettingsAction(strings(R.string.ui_private_mode)) { (context as? com.takeruf.nagi.MainActivity)?.openPrivateBrowsing() }
                                Text(strings(R.string.ui_private_description), Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                SettingsAction(strings(R.string.ui_clear_browsing_history_a2d6ed)) { clearHistory = true }
                                SettingsAction(strings(R.string.ui_clear_site_data)) { clearSiteData = true }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Text(strings(R.string.ui_local_storage_privacy), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                SettingsAction(strings(R.string.ui_privacy_policy)) { uriHandler.openUri(strings(R.string.ui_privacy_policy_url)) }
                            } }
                            if (category == SettingsCategory.ABOUT) {
                                item { SettingsSection(strings(R.string.ui_about_nagi), NagiIcons.Info, showHeading = false) {
                                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("Nagi", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                        Text(com.takeruf.nagi.BuildConfig.VERSION_NAME, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } }
                                item { updateSection() }
                            }
                        }
                    }
                }
            }
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
private fun SettingsSection(title: String, icon: ImageVector, showHeading: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.widthIn(max = 1000.dp)) {
        if (showHeading) Row(Modifier.padding(start = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically,
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
private fun SettingToggle(title: String, subtitle: String?, value: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    Row(modifier.fillMaxWidth().clip(NagiShapes.Rounded).toggleable(value = value, role = Role.Switch, onValueChange = onChange).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 20.dp)) {
            Text(title)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = value, onCheckedChange = null)
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
