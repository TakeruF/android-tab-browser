package com.takeruf.nagi

import android.app.Application
import androidx.datastore.preferences.core.*
import androidx.room.Room
import com.takeruf.nagi.browser.search.AiSearchEngines
import com.takeruf.nagi.browser.search.RegionalSearchDefaults
import com.takeruf.nagi.browser.search.SearchRegion
import com.takeruf.nagi.data.datastore.SettingsStore
import com.takeruf.nagi.data.repository.WorkspaceRepository
import com.takeruf.nagi.data.room.NagiDatabase
import com.takeruf.nagi.domain.model.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = Application::class)
class SettingsPersistenceTest {
    private val context: Application get() = RuntimeEnvironment.getApplication()

    private val saved = BrowserSettings(
        theme = ThemeMode.DARK, sidebarWidth = 337f, sidebarCollapsed = true,
        defaultSearchEngineId = "bing", selectedSpaceId = "work", restoreTabs = false,
        desktopDefault = false, openLinksInNewTab = true, archivePeriod = ArchivePeriod.WEEK,
        automaticSearchRegion = false, searchRegionCountry = "CN", searchRegionSource = "IP",
        searchRegionCheckedAt = 123_456L, themeColor = 0xFF123456,
        commonSearchEngineIds = setOf("bing", "perplexity", "baidu"), defaultAiEngineId = "perplexity",
        nativePageDrag = false, blockExternalApps = true, autoVideoPip = false, videoPopups = false,
        adBlockingEnabled = false, adBlockExcludedHosts = setOf("ads.example"),
        newTabBackground = NewTabBackground.PHOTO, newTabColor = 0xFFABCDEF,
        newTabPhoto = "12345678-1234-1234-1234-123456789012.jpg", translationTargetLanguage = "ja",
        alwaysTranslateLanguages = mapOf("en" to "ja", "fr" to "ko"), bottomSearchBar = true,
        downloadFolder = "content://example.documents/tree/downloads", avoidCameraOverlap = true,
        cameraSideMargin = 47, cameraLeftRotation = 3, spacesEnabled = false,
        commandBar = CommandBarSettings(false, false, false, false, false),
        adaptiveDarkMode = true, darkModeExcludedHosts = setOf("light.example"),
    )

    private class SettingsFile(val context: Application) {
        val file = File.createTempFile("settings-persistence", ".preferences_pb").apply { delete() }
        private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var data = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            private set
        val settings get() = SettingsStore(context, data)

        suspend fun reopen() {
            scope.coroutineContext.job.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            data = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        }

        suspend fun close() {
            scope.coroutineContext.job.cancelAndJoin()
            file.delete()
        }
    }

    private suspend fun withSettings(block: suspend (SettingsFile) -> Unit) {
        val file = SettingsFile(context)
        try { block(file) } finally { file.close() }
    }

    @Test fun everySettingSurvivesDiskReopenAndConcurrentUnrelatedEdits() = runBlocking {
        // Keep every field non-default so omissions from either persistence path are observable.
        BrowserSettings::class.java.declaredFields.filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .forEach { field ->
                field.isAccessible = true
                assertNotEquals("Fixture must exercise ${field.name}", field.get(BrowserSettings()), field.get(saved))
            }
        withSettings { file ->
            file.data.edit { it[stringPreferencesKey("future_setting")] = "keep" }
            file.settings.markAiEnginesSeeded()
            file.settings.markAdditionalEnginesSeeded()
            file.settings.markRegionalEngineSeeded()
            file.settings.update { saved }
            file.reopen()
            assertEquals(saved, file.settings.settings.first())
            val settings = file.settings
            coroutineScope {
                launch { settings.update { it.copy(theme = ThemeMode.LIGHT) } }
                launch { settings.update { it.copy(sidebarWidth = 360f) } }
                launch { settings.update { it.copy(commandBar = it.commandBar.copy(showHistory = true)) } }
                launch { settings.update { it.copy(darkModeExcludedHosts = it.darkModeExcludedHosts + "another.example") } }
                launch { settings.update { it.copy(alwaysTranslateLanguages = it.alwaysTranslateLanguages + ("de" to "ja")) } }
            }
            file.reopen()
            assertEquals(saved.copy(theme = ThemeMode.LIGHT, sidebarWidth = 360f,
                commandBar = saved.commandBar.copy(showHistory = true),
                darkModeExcludedHosts = saved.darkModeExcludedHosts + "another.example",
                alwaysTranslateLanguages = saved.alwaysTranslateLanguages + ("de" to "ja")), file.settings.settings.first())
            assertEquals("keep", file.data.data.first()[stringPreferencesKey("future_setting")])
            assertTrue(file.settings.aiEnginesSeeded())
            assertTrue(file.settings.additionalEnginesSeeded())
            assertTrue(file.settings.regionalEngineSeeded())
        }
    }

    @Test fun shippedPreferenceKeysRetainAllSavedChoicesDuringAnUnrelatedUpdate() = runBlocking {
        withSettings { file ->
            // A raw on-disk fixture detects accidental storage-key renames as well as missing fields.
            file.data.edit { p ->
                p[stringPreferencesKey("theme")] = "DARK"
                p[floatPreferencesKey("sidebar_width")] = 337f
                p[booleanPreferencesKey("sidebar_collapsed")] = true
                p[stringPreferencesKey("default_search_engine")] = "bing"
                p[stringPreferencesKey("selected_space")] = "work"
                p[booleanPreferencesKey("restore_tabs")] = false
                p[booleanPreferencesKey("desktop_default")] = false
                p[booleanPreferencesKey("open_links_new_tab")] = true
                p[stringPreferencesKey("archive_period")] = "WEEK"
                p[booleanPreferencesKey("automatic_search_region")] = false
                p[stringPreferencesKey("search_region_country")] = "CN"
                p[stringPreferencesKey("search_region_source")] = "IP"
                p[longPreferencesKey("search_region_checked_at")] = 123_456L
                p[longPreferencesKey("accent_color")] = 0xFF123456
                p[stringSetPreferencesKey("common_search_engines")] = setOf("bing", "perplexity", "baidu")
                p[stringPreferencesKey("default_ai_engine")] = "perplexity"
                p[booleanPreferencesKey("native_page_drag")] = false
                p[booleanPreferencesKey("block_external_apps")] = true
                p[booleanPreferencesKey("auto_video_pip")] = false
                p[booleanPreferencesKey("video_popups")] = false
                p[booleanPreferencesKey("ad_blocking")] = false
                p[stringSetPreferencesKey("ad_block_excluded_hosts")] = setOf("ads.example")
                p[stringPreferencesKey("new_tab_background")] = "PHOTO"
                p[longPreferencesKey("new_tab_color")] = 0xFFABCDEF
                p[stringPreferencesKey("new_tab_photo")] = "12345678-1234-1234-1234-123456789012.jpg"
                p[stringPreferencesKey("translation_target_language")] = "ja"
                p[stringSetPreferencesKey("always_translate_languages")] = setOf("en:ja", "fr:ko")
                p[booleanPreferencesKey("bottom_search_bar")] = true
                p[stringPreferencesKey("download_folder")] = "content://example.documents/tree/downloads"
                p[booleanPreferencesKey("avoid_camera_overlap")] = true
                p[intPreferencesKey("camera_side_margin")] = 47
                p[intPreferencesKey("camera_left_rotation")] = 3
                p[booleanPreferencesKey("spaces_enabled")] = false
                listOf("search", "tabs", "history", "spaces", "commands").forEach {
                    p[booleanPreferencesKey("command_bar_$it")] = false
                }
                p[booleanPreferencesKey("adaptive_dark_mode")] = true
                p[stringSetPreferencesKey("dark_mode_excluded_hosts")] = setOf("light.example")
            }
            file.reopen()
            assertEquals(saved, file.settings.settings.first())
            file.settings.update { it.copy(theme = ThemeMode.LIGHT) }
            file.reopen()
            assertEquals(saved.copy(theme = ThemeMode.LIGHT), file.settings.settings.first())
        }
    }

    @Test fun legacyManualSearchChoiceSurvivesStartupAndRegionalRefresh() = runBlocking {
        withSettings { file ->
            file.data.edit { it[stringPreferencesKey("default_search_engine")] = "bing" }
            val settings = file.settings
            assertFalse(settings.settings.first().automaticSearchRegion)
            val database = Room.inMemoryDatabaseBuilder(context, NagiDatabase::class.java)
                .allowMainThreadQueries().build()
            try {
                val workspace = WorkspaceRepository(database, settings)
                workspace.initialize()
                var lookups = 0
                RegionalSearchDefaults(workspace, settings, { lookups++; "CN" }, { SearchRegion("CN", "Device region") })
                    .refresh(force = true)
                assertEquals(0, lookups)
                assertEquals("bing", settings.settings.first().defaultSearchEngineId)
                file.reopen()
                assertFalse(file.settings.settings.first().automaticSearchRegion)
                assertEquals("bing", file.settings.settings.first().defaultSearchEngineId)
            } finally { database.close() }
        }
    }

    @Test fun clearedOptionalValuesAndEmptySelectionsSurviveReopening() = runBlocking {
        withSettings { file ->
            file.settings.update { saved }
            val cleared = saved.copy(defaultAiEngineId = null, commonSearchEngineIds = emptySet(),
                newTabPhoto = null, translationTargetLanguage = null, alwaysTranslateLanguages = emptyMap(),
                downloadFolder = null, cameraLeftRotation = null, searchRegionCountry = null,
                searchRegionSource = null, searchRegionCheckedAt = 0,
                adBlockExcludedHosts = emptySet(), darkModeExcludedHosts = emptySet())
            file.settings.update { cleared }
            file.reopen()
            assertEquals(cleared, file.settings.settings.first())
            file.settings.update { it.copy(commonSearchEngineIds = null) }
            file.reopen()
            assertEquals(cleared.copy(commonSearchEngineIds = null), file.settings.settings.first())
        }
    }

    @Test fun legacyAiChoiceSurvivesAnUnrelatedWriteBeforeTheFirstRead() = runBlocking {
        withSettings { file ->
            file.data.edit { it[stringPreferencesKey("default_search_engine")] = "perplexity" }
            file.settings.update { it.copy(sidebarCollapsed = true) }
            file.reopen()
            val restored = file.settings.settings.first()
            assertFalse(restored.automaticSearchRegion)
            assertEquals("google", restored.defaultSearchEngineId)
            assertEquals("perplexity", AiSearchEngines.defaultId(restored))
            assertTrue(restored.sidebarCollapsed)
        }
    }

    @Test fun freshInstallAndExplicitAutomaticChoicesKeepTheirDefaults() = runBlocking {
        withSettings { file ->
            assertEquals(BrowserSettings(), file.settings.settings.first())
            file.settings.update { it.copy(sidebarCollapsed = true) }
            file.reopen()
            assertTrue(file.settings.settings.first().automaticSearchRegion)
            file.data.edit {
                it[stringPreferencesKey("default_search_engine")] = "bing"
                it[booleanPreferencesKey("automatic_search_region")] = true
            }
            file.settings.update { it.copy(bottomSearchBar = true) }
            file.reopen()
            assertTrue(file.settings.settings.first().automaticSearchRegion)
            file.data.edit { it[booleanPreferencesKey("automatic_search_region")] = false }
            file.reopen()
            assertFalse(file.settings.settings.first().automaticSearchRegion)
        }
    }
}
