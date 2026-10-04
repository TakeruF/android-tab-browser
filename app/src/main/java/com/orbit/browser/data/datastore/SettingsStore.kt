package com.orbit.browser.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.orbit.browser.domain.model.*
import kotlinx.coroutines.flow.*
import java.io.IOException

private val Context.browserDataStore by preferencesDataStore("browser_settings")

class SettingsStore(context: Context, private val store: androidx.datastore.core.DataStore<Preferences> = context.applicationContext.browserDataStore) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        // Keep the original storage key so existing color choices survive this rename.
        val themeColor = longPreferencesKey("accent_color")
        val width = floatPreferencesKey("sidebar_width")
        val collapsed = booleanPreferencesKey("sidebar_collapsed")
        val engine = stringPreferencesKey("default_search_engine")
        val space = stringPreferencesKey("selected_space")
        val restore = booleanPreferencesKey("restore_tabs")
        val desktop = booleanPreferencesKey("desktop_default")
        val newLinks = booleanPreferencesKey("open_links_new_tab")
        val additionalEnginesSeeded = booleanPreferencesKey("additional_engines_seeded_v1")
        val regionalEngineSeeded = booleanPreferencesKey("regional_engine_seeded")
        val automaticRegion = booleanPreferencesKey("automatic_search_region")
        val regionCountry = stringPreferencesKey("search_region_country")
        val regionSource = stringPreferencesKey("search_region_source")
        val regionCheckedAt = longPreferencesKey("search_region_checked_at")
        val archive = stringPreferencesKey("archive_period")
    }
    val settings: Flow<BrowserSettings> = store.data.catch {
        if (it is IOException) emit(emptyPreferences()) else throw it
    }.map { p -> BrowserSettings(
        theme = enumOrDefault(p[Keys.theme], ThemeMode.SYSTEM),
        sidebarWidth = (p[Keys.width] ?: 264f).coerceIn(220f, 380f),
        sidebarCollapsed = p[Keys.collapsed] ?: false,
        defaultSearchEngineId = p[Keys.engine] ?: "google",
        selectedSpaceId = p[Keys.space] ?: "personal",
        restoreTabs = p[Keys.restore] ?: true, desktopDefault = p[Keys.desktop] ?: false,
        openLinksInNewTab = p[Keys.newLinks] ?: false,
        archivePeriod = enumOrDefault(p[Keys.archive], ArchivePeriod.NEVER),
        automaticSearchRegion = p[Keys.automaticRegion] ?: (p[Keys.engine] == null),
        searchRegionCountry = p[Keys.regionCountry], searchRegionSource = p[Keys.regionSource],
        searchRegionCheckedAt = p[Keys.regionCheckedAt] ?: 0,
        themeColor = p[Keys.themeColor] ?: 0xFF426B5A,
    ) }
    suspend fun additionalEnginesSeeded(): Boolean = store.data.first()[Keys.additionalEnginesSeeded] ?: false
    suspend fun markAdditionalEnginesSeeded() { store.edit { it[Keys.additionalEnginesSeeded] = true } }
    suspend fun regionalEngineSeeded(): Boolean = store.data.first()[Keys.regionalEngineSeeded] ?: false
    suspend fun markRegionalEngineSeeded() { store.edit { it[Keys.regionalEngineSeeded] = true } }
    suspend fun update(change: (BrowserSettings) -> BrowserSettings) {
        store.edit { p ->
            // Read inside the atomic edit to prevent concurrent settings changes being lost.
            val current = BrowserSettings(
                enumOrDefault(p[Keys.theme], ThemeMode.SYSTEM), p[Keys.width] ?: 264f,
                p[Keys.collapsed] ?: false, p[Keys.engine] ?: "google", p[Keys.space] ?: "personal",
                p[Keys.restore] ?: true, p[Keys.desktop] ?: false, p[Keys.newLinks] ?: false,
                enumOrDefault(p[Keys.archive], ArchivePeriod.NEVER),
                p[Keys.automaticRegion] ?: (p[Keys.engine] == null),
                p[Keys.regionCountry], p[Keys.regionSource], p[Keys.regionCheckedAt] ?: 0,
                p[Keys.themeColor] ?: 0xFF426B5A)
            val next = change(current)
            p[Keys.themeColor] = next.themeColor
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
    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback
}
