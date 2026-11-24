package com.takeruf.nagi.data.datastore

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.takeruf.nagi.domain.model.*
import com.takeruf.nagi.browser.search.AiSearchEngines
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.delay
import java.io.IOException

private val Context.browserDataStore by preferencesDataStore("browser_settings")

class SettingsStore(context: Context, private val store: androidx.datastore.core.DataStore<Preferences> = context.applicationContext.browserDataStore) {
    private object Keys {
        val spacesEnabled = booleanPreferencesKey("spaces_enabled")
        val commandBarSearch = booleanPreferencesKey("command_bar_search")
        val commandBarTabs = booleanPreferencesKey("command_bar_tabs")
        val commandBarHistory = booleanPreferencesKey("command_bar_history")
        val commandBarSpaces = booleanPreferencesKey("command_bar_spaces")
        val commandBarCommands = booleanPreferencesKey("command_bar_commands")
        val commandBarLayout = stringPreferencesKey("command_bar_layout")
        val commandBarLayoutNoticeDismissed = booleanPreferencesKey("command_bar_layout_notice_dismissed")
        val avoidCameraOverlap = booleanPreferencesKey("avoid_camera_overlap")
        val cameraLeftRotation = intPreferencesKey("camera_left_rotation")
        val cameraSideMargin = intPreferencesKey("camera_side_margin")
        val bottomSearchBar = booleanPreferencesKey("bottom_search_bar")
        val downloadFolder = stringPreferencesKey("download_folder")
        val adaptiveDarkMode = booleanPreferencesKey("adaptive_dark_mode")
        val darkModeExcludedHosts = stringSetPreferencesKey("dark_mode_excluded_hosts")
        val theme = stringPreferencesKey("theme")
        val compactUiMode = stringPreferencesKey("compact_ui_mode")
        // Keep the original storage key so existing color choices survive this rename.
        val themeColor = longPreferencesKey("accent_color")
        val width = floatPreferencesKey("sidebar_width")
        val collapsed = booleanPreferencesKey("sidebar_collapsed")
        val engine = stringPreferencesKey("default_search_engine")
        val aiEngine = stringPreferencesKey("default_ai_engine")
        val commonEngines = stringSetPreferencesKey("common_search_engines")
        val space = stringPreferencesKey("selected_space")
        val restore = booleanPreferencesKey("restore_tabs")
        val desktop = booleanPreferencesKey("desktop_default")
        val newLinks = booleanPreferencesKey("open_links_new_tab")
        val blockExternal = booleanPreferencesKey("block_external_apps")
        val autoVideoPip = booleanPreferencesKey("auto_video_pip")
        val videoPopups = booleanPreferencesKey("video_popups")
        val adBlocking = booleanPreferencesKey("ad_blocking")
        val adBlockExcluded = stringSetPreferencesKey("ad_block_excluded_hosts")
        val pageDrag = booleanPreferencesKey("native_page_drag")
        val aiEnginesSeeded = booleanPreferencesKey("ai_engines_seeded_v1")
        val additionalEnginesSeeded = booleanPreferencesKey("additional_engines_seeded_v1")
        val regionalEngineSeeded = booleanPreferencesKey("regional_engine_seeded")
        val automaticRegion = booleanPreferencesKey("automatic_search_region")
        val regionCountry = stringPreferencesKey("search_region_country")
        val regionSource = stringPreferencesKey("search_region_source")
        val regionCheckedAt = longPreferencesKey("search_region_checked_at")
        val newTabBackground = stringPreferencesKey("new_tab_background")
        val newTabColor = longPreferencesKey("new_tab_color")
        val newTabPhoto = stringPreferencesKey("new_tab_photo")
        val archive = stringPreferencesKey("archive_period")
        val translationTargetLanguage = stringPreferencesKey("translation_target_language")
        val alwaysTranslateLanguages = stringSetPreferencesKey("always_translate_languages")
    }
    private val preferences = store.data.retryWhen { error, _ ->
        if (error is IOException && error !is CorruptionException) {
            // A failed read is not an empty store. Keep observing until storage recovers.
            delay(500)
            true
        } else false
    }
    val settings: Flow<BrowserSettings> = preferences.map(::readSettings)

    private fun readSettings(p: Preferences) = BrowserSettings(
        spacesEnabled = p[Keys.spacesEnabled] ?: true,
        commandBar = commandBarSettings(p),
        commandBarLayout = enumOrDefault(p[Keys.commandBarLayout], CommandBarLayout.ANCHORED),
        commandBarLayoutNoticeDismissed = p[Keys.commandBarLayoutNoticeDismissed] ?: false,
        bottomSearchBar = p[Keys.bottomSearchBar] ?: false,
        downloadFolder = p[Keys.downloadFolder],
        avoidCameraOverlap = p[Keys.avoidCameraOverlap] ?: false,
        cameraSideMargin = (p[Keys.cameraSideMargin] ?: 32).coerceIn(0, 64),
        cameraLeftRotation = p[Keys.cameraLeftRotation]?.takeIf { it in 0..3 },
        newTabBackground = enumOrDefault(p[Keys.newTabBackground], NewTabBackground.DEFAULT),
        newTabColor = p[Keys.newTabColor] ?: 0xFF426B5A,
        newTabPhoto = p[Keys.newTabPhoto],
        translationTargetLanguage = p[Keys.translationTargetLanguage],
        alwaysTranslateLanguages = translationRules(p[Keys.alwaysTranslateLanguages]),
        adaptiveDarkMode = p[Keys.adaptiveDarkMode] ?: false,
        darkModeExcludedHosts = p[Keys.darkModeExcludedHosts] ?: emptySet(),
        theme = enumOrDefault(p[Keys.theme], ThemeMode.SYSTEM),
        compactUiMode = enumOrDefault(p[Keys.compactUiMode], CompactUiMode.AUTO),
        sidebarWidth = (p[Keys.width] ?: 264f).coerceIn(220f, 380f),
        sidebarCollapsed = p[Keys.collapsed] ?: false,
        defaultSearchEngineId = p[Keys.engine]?.takeUnless { it in AiSearchEngines.ids } ?: "google",
        selectedSpaceId = p[Keys.space] ?: "personal",
        restoreTabs = p[Keys.restore] ?: true, desktopDefault = p[Keys.desktop] ?: true,
        openLinksInNewTab = p[Keys.newLinks] ?: false,
        nativePageDrag = p[Keys.pageDrag] ?: true,
        blockExternalApps = p[Keys.blockExternal] ?: false,
        autoVideoPip = p[Keys.autoVideoPip] ?: true,
        videoPopups = p[Keys.videoPopups] ?: true,
        adBlockingEnabled = p[Keys.adBlocking] ?: true,
        adBlockExcludedHosts = p[Keys.adBlockExcluded] ?: emptySet(),
        archivePeriod = enumOrDefault(p[Keys.archive], ArchivePeriod.NEVER),
        // An engine saved before automatic region selection existed is a manual choice.
        automaticSearchRegion = p[Keys.automaticRegion] ?: (p[Keys.engine] == null),
        searchRegionCountry = p[Keys.regionCountry], searchRegionSource = p[Keys.regionSource],
        searchRegionCheckedAt = p[Keys.regionCheckedAt] ?: 0,
        themeColor = p[Keys.themeColor] ?: 0xFF426B5A,
        commonSearchEngineIds = p[Keys.commonEngines],
        defaultAiEngineId = p[Keys.aiEngine] ?: p[Keys.engine]?.takeIf { it in AiSearchEngines.ids },
    )
    suspend fun aiEnginesSeeded(): Boolean = preferences.first()[Keys.aiEnginesSeeded] ?: false
    suspend fun markAiEnginesSeeded() { store.edit { it[Keys.aiEnginesSeeded] = true } }
    suspend fun additionalEnginesSeeded(): Boolean = preferences.first()[Keys.additionalEnginesSeeded] ?: false
    suspend fun markAdditionalEnginesSeeded() { store.edit { it[Keys.additionalEnginesSeeded] = true } }
    suspend fun regionalEngineSeeded(): Boolean = preferences.first()[Keys.regionalEngineSeeded] ?: false
    suspend fun markRegionalEngineSeeded() { store.edit { it[Keys.regionalEngineSeeded] = true } }
    suspend fun update(change: (BrowserSettings) -> BrowserSettings) {
        store.edit { p ->
            // Read inside the atomic edit to prevent concurrent settings changes being lost.
            val current = readSettings(p)
            val next = change(current)
            p[Keys.adaptiveDarkMode] = next.adaptiveDarkMode
            p[Keys.darkModeExcludedHosts] = next.darkModeExcludedHosts
            p[Keys.spacesEnabled] = next.spacesEnabled
            p[Keys.commandBarSearch] = next.commandBar.showSearch
            p[Keys.commandBarTabs] = next.commandBar.showTabs
            p[Keys.commandBarHistory] = next.commandBar.showHistory
            p[Keys.commandBarSpaces] = next.commandBar.showSpaces
            p[Keys.commandBarCommands] = next.commandBar.showCommands
            p[Keys.commandBarLayout] = next.commandBarLayout.name
            p[Keys.commandBarLayoutNoticeDismissed] = next.commandBarLayoutNoticeDismissed
            p[Keys.avoidCameraOverlap] = next.avoidCameraOverlap
            p[Keys.cameraSideMargin] = next.cameraSideMargin.coerceIn(0, 64)
            next.cameraLeftRotation?.takeIf { it in 0..3 }?.let { p[Keys.cameraLeftRotation] = it }
                ?: p.remove(Keys.cameraLeftRotation)
            p[Keys.bottomSearchBar] = next.bottomSearchBar
            next.downloadFolder?.let { p[Keys.downloadFolder] = it } ?: p.remove(Keys.downloadFolder)
            next.translationTargetLanguage?.let { p[Keys.translationTargetLanguage] = it } ?: p.remove(Keys.translationTargetLanguage)
            p[Keys.alwaysTranslateLanguages] = next.alwaysTranslateLanguages.filter { (source, target) -> source != target }
                .map { (source, target) -> "$source:$target" }.toSet()
            p[Keys.newTabBackground] = next.newTabBackground.name
            p[Keys.newTabColor] = next.newTabColor
            next.newTabPhoto?.let { p[Keys.newTabPhoto] = it } ?: p.remove(Keys.newTabPhoto)
            p[Keys.adBlocking] = next.adBlockingEnabled
            p[Keys.adBlockExcluded] = next.adBlockExcludedHosts
            p[Keys.autoVideoPip] = next.autoVideoPip
            p[Keys.videoPopups] = next.videoPopups
            p[Keys.pageDrag] = next.nativePageDrag
            p[Keys.blockExternal] = next.blockExternalApps
            next.defaultAiEngineId?.let { p[Keys.aiEngine] = it } ?: p.remove(Keys.aiEngine)
            next.commonSearchEngineIds?.let { p[Keys.commonEngines] = it } ?: p.remove(Keys.commonEngines)
            p[Keys.themeColor] = next.themeColor
            p[Keys.compactUiMode] = next.compactUiMode.name
            p[Keys.theme] = next.theme.name; p[Keys.width] = next.sidebarWidth.coerceIn(220f, 380f)
            p[Keys.collapsed] = next.sidebarCollapsed; p[Keys.engine] = next.defaultSearchEngineId
            p[Keys.space] = next.selectedSpaceId; p[Keys.restore] = next.restoreTabs
            p[Keys.desktop] = next.desktopDefault; p[Keys.newLinks] = next.openLinksInNewTab
            p[Keys.archive] = next.archivePeriod.name
            p[Keys.automaticRegion] = next.automaticSearchRegion
            next.searchRegionCountry?.let { p[Keys.regionCountry] = it } ?: p.remove(Keys.regionCountry)
            next.searchRegionSource?.let { p[Keys.regionSource] = it } ?: p.remove(Keys.regionSource)
            p[Keys.regionCheckedAt] = next.searchRegionCheckedAt
        }
    }
    private fun commandBarSettings(p: Preferences) = CommandBarSettings(
        showSearch = p[Keys.commandBarSearch] ?: true,
        showTabs = p[Keys.commandBarTabs] ?: true,
        showHistory = p[Keys.commandBarHistory] ?: true,
        showSpaces = p[Keys.commandBarSpaces] ?: true,
        showCommands = p[Keys.commandBarCommands] ?: true,
    )

    private fun translationRules(values: Set<String>?): Map<String, String> = values.orEmpty().mapNotNull { value ->
        val parts = value.split(':')
        if (parts.size == 2 && parts.all { it.isNotBlank() } && parts[0] != parts[1]) parts[0] to parts[1] else null
    }.toMap()

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback
}
