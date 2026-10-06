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
    }
    @Test fun outageUsesDeviceAndManualSelectionStopsFutureLookup() = runBlocking {
        var calls = 0
        val regional = RegionalSearchDefaults(workspace, settings, { calls++; throw java.io.IOException() }, { SearchRegion("CN", "Device region") })
        regional.refresh()
        assertEquals("baidu", settings.settings.first().defaultSearchEngineId)
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
}
