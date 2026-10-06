package com.takeruf.nagi

import android.app.Application
import androidx.room.Room
import com.takeruf.nagi.browser.search.*
import com.takeruf.nagi.data.datastore.SettingsStore
import com.takeruf.nagi.data.repository.*
import com.takeruf.nagi.data.room.NagiDatabase
import com.takeruf.nagi.domain.model.BrowserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RegionalSearchTest {
    private lateinit var db: NagiDatabase
    private lateinit var settings: SettingsStore
    private lateinit var workspace: WorkspaceRepository
    @Before fun setup() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        settings = SettingsStore(context); settings.update { BrowserSettings() }
        db = Room.inMemoryDatabaseBuilder(context, NagiDatabase::class.java).allowMainThreadQueries().build()
        workspace = WorkspaceRepository(db, settings); workspace.initialize()
    }
    @After fun close() { db.close() }
    @Test fun separateDefaultsPersistAndDeletingAiFallsBack() = runBlocking {
        val engines = SearchEngineRepository(workspace, settings)
        engines.setDefault("bing")
        engines.setDefaultAi("perplexity")
        settings.update { it.copy(theme = com.takeruf.nagi.domain.model.ThemeMode.DARK) }
        val reloaded = SettingsStore(RuntimeEnvironment.getApplication()).settings.first()
        assertEquals("bing", reloaded.defaultSearchEngineId)
        assertEquals("perplexity", reloaded.defaultAiEngineId)
        assertTrue(CommonSearchEngines.ids(reloaded).containsAll(setOf("bing", "perplexity")))
        engines.delete("perplexity")
        assertEquals("chatgpt", settings.settings.first().defaultAiEngineId)
        assertEquals("bing", settings.settings.first().defaultSearchEngineId)
    }

    @Test fun onlyMainlandCountryUsesBaiduAndFallbackDoesNotGuessLanguage() {
        assertEquals("baidu", RegionalSearchPolicy.engine("CN"))
        listOf("HK", "MO", "TW", "JP", "US").forEach { assertEquals("google", RegionalSearchPolicy.engine(it)) }
        assertEquals("CN", RegionalSearchPolicy.country("cn"))
        assertNull(RegionalSearchPolicy.country("zh-Hans")); assertNull(RegionalSearchPolicy.country("XX"))
        assertEquals(SearchRegion("JP", "Mobile network"), RegionalSearchPolicy.fallback("jp", "cn", "CN"))
        assertEquals(SearchRegion("CN", "SIM"), RegionalSearchPolicy.fallback("", "cn", "JP"))
        assertNull(RegionalSearchPolicy.fallback(null, null, ""))
    }
    @Test fun ipOverridesDeviceAndSuccessfulResultIsCached() = runBlocking {
        var calls = 0
        val regional = RegionalSearchDefaults(workspace, settings, { calls++; "CN" }, { SearchRegion("JP", "Device region") }, { 100_000L })
        regional.refresh(); regional.refresh()
        assertEquals(1, calls); assertEquals("baidu", settings.settings.first().defaultSearchEngineId)
        assertEquals("IP", settings.settings.first().searchRegionSource)
        assertEquals(setOf("baidu", "qwen"), CommonSearchEngines.ids(settings.settings.first()))
        assertNull(settings.settings.first().commonSearchEngineIds)
    }
    @Test fun automaticRegionChangeUpdatesUncustomizedCommonDefaults() = runBlocking {
        var country = "CN"
        val regional = RegionalSearchDefaults(workspace, settings, { country }, { null })
        regional.refresh(true)
        assertEquals(setOf("baidu", "qwen"), CommonSearchEngines.ids(settings.settings.first()))
        assertEquals("qwen", AiSearchEngines.default(settings.settings.first(), DefaultSearchEngines.all).id)
        country = "JP"
        regional.refresh(true)
        assertEquals(setOf("google", "chatgpt"), CommonSearchEngines.ids(settings.settings.first()))
        assertEquals("chatgpt", AiSearchEngines.defaultId(settings.settings.first()))
    }
    @Test fun manualAiChoiceStopsAutomaticRegionUntilEnabledAgain() = runBlocking {
        val regional = RegionalSearchDefaults(workspace, settings, { "CN" }, { null })
        regional.refresh(true)
        SearchEngineRepository(workspace, settings).setDefaultAi("chatgpt")
        regional.refresh(true)
        assertFalse(settings.settings.first().automaticSearchRegion)
        assertEquals("chatgpt", AiSearchEngines.defaultId(settings.settings.first()))
        settings.update { it.copy(automaticSearchRegion = true) }
        regional.refresh(true)
        assertEquals("qwen", AiSearchEngines.defaultId(settings.settings.first()))
    }
    @Test fun outageUsesDeviceAndManualSelectionStopsFutureLookup() = runBlocking {
        var calls = 0
        val regional = RegionalSearchDefaults(workspace, settings, { calls++; throw java.io.IOException() }, { SearchRegion("CN", "Device region") })
        regional.refresh()
        assertEquals("baidu", settings.settings.first().defaultSearchEngineId)
        assertEquals(setOf("baidu", "qwen"), CommonSearchEngines.ids(settings.settings.first()))
        SearchEngineRepository(workspace, settings).setDefault("ddg")
        regional.refresh(true)
        assertEquals(1, calls); assertEquals("ddg", settings.settings.first().defaultSearchEngineId)
        assertFalse(settings.settings.first().automaticSearchRegion)
    }
    @Test fun manualChoiceDuringLookupWinsAndDeletedEngineIsNotRecreated() = runBlocking {
        val regional = RegionalSearchDefaults(workspace, settings, {
            SearchEngineRepository(workspace, settings).setDefault("bing"); "CN"
        }, { null })
        regional.refresh()
        assertEquals("bing", settings.settings.first().defaultSearchEngineId)
        SearchEngineRepository(workspace, settings).delete("baidu")
        settings.update { it.copy(automaticSearchRegion = true) }
        RegionalSearchDefaults(workspace, settings, { "CN" }, { null }).refresh(true)
        assertEquals("bing", settings.settings.first().defaultSearchEngineId)
        assertTrue(db.browserDao().engines().none { it.id == "baidu" })
    }
    @Test fun unknownRegionKeepsEngineAndRetriesAfterShortCache() = runBlocking {
        var calls = 0; var time = 100_000L
        val regional = RegionalSearchDefaults(workspace, settings, { calls++; null }, { null }, { time })
        regional.refresh(); regional.refresh()
        assertEquals(1, calls); assertEquals("google", settings.settings.first().defaultSearchEngineId)
        time += 3_600_001L; regional.refresh()
        assertEquals(2, calls)
    }
    @Test fun commonChoicesPersistAndStopRegionFromReplacingTravelersCombination() = runBlocking {
        settings.update { CommonSearchEngines.select(it, "baidu", true) }
        val reloaded = SettingsStore(RuntimeEnvironment.getApplication()).settings.first()
        assertEquals(setOf("google", "baidu", "chatgpt"), reloaded.commonSearchEngineIds)
        var calls = 0
        RegionalSearchDefaults(workspace, settings, { calls++; "CN" }, { null }).refresh(true)
        assertEquals(0, calls)
        assertEquals("google", settings.settings.first().defaultSearchEngineId)
        settings.update { it.copy(theme = com.takeruf.nagi.domain.model.ThemeMode.DARK) }
        assertEquals(reloaded.commonSearchEngineIds, settings.settings.first().commonSearchEngineIds)
    }
    @Test fun changingDefaultKeepsCommonChoicesAndDeletingEngineCleansSelection() = runBlocking {
        settings.update { CommonSearchEngines.select(it, "baidu", true) }
        val repository = SearchEngineRepository(workspace, settings)
        repository.setDefault("bing")
        assertEquals(setOf("google", "baidu", "chatgpt", "bing"), CommonSearchEngines.ids(settings.settings.first()))
        repository.delete("baidu")
        assertFalse("baidu" in settings.settings.first().commonSearchEngineIds.orEmpty())
        repository.delete("bing")
        val saved = settings.settings.first()
        assertFalse("bing" in saved.commonSearchEngineIds.orEmpty())
        assertTrue(saved.defaultSearchEngineId in CommonSearchEngines.ids(saved))
    }
}
