package com.takeruf.nagi

import android.app.Application
import androidx.room.Room
import com.takeruf.nagi.data.datastore.SettingsStore
import com.takeruf.nagi.data.repository.*
import com.takeruf.nagi.data.room.NagiDatabase
import kotlinx.coroutines.*
import com.takeruf.nagi.browser.search.AndroidSearchRegion
import com.takeruf.nagi.browser.search.RegionalSearchDefaults

class NagiApplication : Application() {
    lateinit var container: AppContainer
        private set
    override fun onCreate() { super.onCreate(); container = AppContainer(this) }
}

class AppContainer(application: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Keep the existing database filename so installed Nagi builds retain their data.
    val database = Room.databaseBuilder(application, NagiDatabase::class.java, "orbit.db").build()
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
