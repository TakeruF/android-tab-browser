package com.takeruf.nagi

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import com.takeruf.nagi.browser.search.DefaultSearchEngines
import com.takeruf.nagi.data.datastore.SettingsStore
import com.takeruf.nagi.data.repository.WorkspaceRepository
import com.takeruf.nagi.data.room.*
import com.takeruf.nagi.domain.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SearchEngineMigrationTest {
    @Test fun previousAiSearchDefaultBecomesSeparateAiDefault() = runBlocking {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
        val file = java.io.File.createTempFile("split-defaults", ".preferences_pb").apply { delete() }
        val store = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        try {
            store.edit { it[stringPreferencesKey("default_search_engine")] = "perplexity" }
            val settings = SettingsStore(RuntimeEnvironment.getApplication(), store)
            assertEquals("google", settings.settings.first().defaultSearchEngineId)
            assertEquals("perplexity", settings.settings.first().defaultAiEngineId)
            settings.update { it.copy(sidebarCollapsed = true) }
            assertEquals("google", settings.settings.first().defaultSearchEngineId)
            assertEquals("perplexity", settings.settings.first().defaultAiEngineId)
        } finally { scope.coroutineContext[kotlinx.coroutines.Job]?.cancel(); file.delete() }
    }

    @Test fun upgradeAddsEnginesOnceWithoutReplacingEditsOrKeywordConflicts() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, NagiDatabase::class.java).allowMainThreadQueries().build()
        val dataScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
        val file = java.io.File.createTempFile("migration", ".preferences_pb").apply { delete() }
        val store = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(scope = dataScope, produceFile = { file })
        try {
            val settings = SettingsStore(context, store)
            settings.update { it.copy(defaultSearchEngineId = "bing", automaticSearchRegion = false) }
            val dao = db.browserDao()
            dao.putSpace(Space("personal", "Renamed by user").entity())
            DefaultSearchEngines.all.filter { it.id !in DefaultSearchEngines.additionalIds && it.id !in DefaultSearchEngines.aiIds }.forEach { dao.putEngine(it.entity()) }
            dao.putEngine(SearchEngine("custom", "My existing engine", "sg", "https://example.com/?q={query}").entity())
            dao.putEngine(SearchEngine("existing-qwen", "Custom Qwen", "qwen", "https://example.com/?q={query}").entity())
            dao.putEngine(SearchEngine("douyin", "Edited Douyin", "video", "https://example.com/video?q={query}").entity())
            val workspace = WorkspaceRepository(db, settings)
            workspace.initialize()
            assertTrue(dao.engines().none { it.id == "qwen" })
            assertTrue(dao.engines().any { it.id == "perplexity" })
            assertTrue(dao.engines().any { it.id == "so360" })
            assertTrue(dao.engines().any { it.id == "shenma" })
            assertTrue(dao.engines().none { it.id == "sogou" })
            assertEquals("Edited Douyin", dao.engines().first { it.id == "douyin" }.name)
            assertEquals("My existing engine", dao.engines().first { it.id == "custom" }.name)
            assertEquals("bing", settings.settings.first().defaultSearchEngineId)
            dao.deleteEngine("so360")
            workspace.initialize()
            assertTrue(dao.engines().none { it.id == "so360" })
        } finally { db.close(); dataScope.coroutineContext[kotlinx.coroutines.Job]?.cancel(); file.delete() }
    }
    @Test fun upgradeAfterPreviousSeedAddsAiEnginesOnceAndPreservesPreferences() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, NagiDatabase::class.java).allowMainThreadQueries().build()
        val dataScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
        val file = java.io.File.createTempFile("ai-migration", ".preferences_pb").apply { delete() }
        val store = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(scope = dataScope, produceFile = { file })
        try {
            val settings = SettingsStore(context, store)
            settings.markAdditionalEnginesSeeded()
            settings.markRegionalEngineSeeded()
            val prefs = BrowserSettings(defaultSearchEngineId = "bing", automaticSearchRegion = false,
                commonSearchEngineIds = setOf("bing", "chatgpt"))
            settings.update { prefs }
            val dao = db.browserDao()
            dao.putSpace(Space("personal", "Personal").entity())
            DefaultSearchEngines.all.filter { it.id !in DefaultSearchEngines.aiIds }.forEach { dao.putEngine(it.entity()) }
            val workspace = WorkspaceRepository(db, settings)
            workspace.initialize()
            assertTrue(dao.engines().any { it.id == "qwen" })
            assertTrue(dao.engines().any { it.id == "perplexity" })
            assertEquals(prefs, settings.settings.first())
            dao.deleteEngine("qwen")
            dao.putEngine(SearchEngine("perplexity", "Edited Perplexity", "pplx", "https://example.com/?q={query}").entity())
            workspace.initialize()
            assertTrue(dao.engines().none { it.id == "qwen" })
            assertEquals("Edited Perplexity", dao.engines().first { it.id == "perplexity" }.name)
        } finally { db.close(); dataScope.coroutineContext[kotlinx.coroutines.Job]?.cancel(); file.delete() }
    }

}