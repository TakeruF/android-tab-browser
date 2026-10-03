package com.orbit.browser

import android.app.Application
import androidx.room.Room
import com.orbit.browser.data.datastore.SettingsStore
import com.orbit.browser.data.repository.*
import com.orbit.browser.data.room.OrbitDatabase
import kotlinx.coroutines.*
import com.orbit.browser.browser.search.AndroidSearchRegion
import com.orbit.browser.browser.search.RegionalSearchDefaults

class OrbitApplication : Application() {
    lateinit var container: AppContainer
        private set
    override fun onCreate() { super.onCreate(); container = AppContainer(this) }
}

class AppContainer(application: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database = Room.databaseBuilder(application, OrbitDatabase::class.java, "orbit.db").build()
    val settings = SettingsStore(application)
    val workspace = WorkspaceRepository(database, settings)
    val tabs = TabRepository(workspace, settings)
    val spaces = SpaceRepository(workspace, settings, tabs)
    val library = LibraryRepository(workspace)
    val engines = SearchEngineRepository(workspace, settings)
    private val region = AndroidSearchRegion(application)
    val regionalSearch = RegionalSearchDefaults(workspace, settings, region::lookup, region::fallback)
    init { scope.launch { runCatching { workspace.initialize(); tabs.archiveNow(); regionalSearch.refresh() } } }
}
