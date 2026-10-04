package com.orbit.browser

import android.app.Application
import androidx.room.Room
import com.orbit.browser.browser.search.DefaultSearchEngines
import com.orbit.browser.data.datastore.SettingsStore
import com.orbit.browser.data.repository.WorkspaceRepository
import com.orbit.browser.data.room.*
import com.orbit.browser.domain.model.*
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
    @Test fun upgradeAddsEnginesOnceWithoutReplacingEditsOrKeywordConflicts() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, OrbitDatabase::class.java).allowMainThreadQueries().build()
        val dataScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
        val file = java.io.File.createTempFile("migration", ".preferences_pb").apply { delete() }
        val store = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(scope = dataScope, produceFile = { file })
        try {
            val settings = SettingsStore(context, store)
            settings.update { it.copy(defaultSearchEngineId = "bing", automaticSearchRegion = false) }
            val dao = db.browserDao()
            dao.putSpace(Space("personal", "Renamed by user").entity())
            DefaultSearchEngines.all.filter { it.id !in DefaultSearchEngines.additionalIds }.forEach { dao.putEngine(it.entity()) }
            dao.putEngine(SearchEngine("custom", "My existing engine", "sg", "https://example.com/?q={query}").entity())
            dao.putEngine(SearchEngine("douyin", "Edited Douyin", "video", "https://example.com/video?q={query}").entity())
            val workspace = WorkspaceRepository(db, settings)
            workspace.initialize()
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
}