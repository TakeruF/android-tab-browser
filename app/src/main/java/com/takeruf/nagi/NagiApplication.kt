package com.takeruf.nagi

import android.app.Application
import androidx.room.Room
import com.takeruf.nagi.data.datastore.SettingsStore
import com.takeruf.nagi.data.repository.*
import com.takeruf.nagi.data.room.NagiDatabase
import com.takeruf.nagi.data.room.model
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import com.takeruf.nagi.browser.search.AndroidSearchRegion
import com.takeruf.nagi.browser.search.RegionalSearchDefaults

class NagiApplication : Application() {
    lateinit var container: AppContainer
        private set
    override fun onCreate() {
        super.onCreate()
        com.takeruf.nagi.browser.privacy.PrivateProfiles.removeStaleProfiles()
        container = AppContainer(this)
    }
}

class AppContainer(application: Application, val privateProfileName: String? = null,
    initialSettings: SettingsStore? = null,
    sharedBlocker: com.takeruf.nagi.browser.blocking.AdBlocker? = null,
    privateEngineSource: WorkspaceRepository? = null) {
    val isPrivate get() = privateProfileName != null
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Keep the existing database filename so installed Nagi builds retain their data.
    val database = if (isPrivate) Room.inMemoryDatabaseBuilder(application, NagiDatabase::class.java).build()
        else Room.databaseBuilder(application, NagiDatabase::class.java, "orbit.db").build()
    val settings = if (isPrivate) SettingsStore(application, com.takeruf.nagi.browser.privacy.MemoryPreferences()) else SettingsStore(application)
    val siteDisplayModes = com.takeruf.nagi.browser.engine.SiteDisplayModeStore(application, inMemory = isPrivate)
    val adBlocker = sharedBlocker ?: com.takeruf.nagi.browser.blocking.AdBlocker(application, scope)
    val workspace = WorkspaceRepository(database, settings)
    val tabs = TabRepository(workspace, settings)
    val spaces = SpaceRepository(workspace, settings, tabs)
    val library = LibraryRepository(workspace)
    val engines = SearchEngineRepository(workspace, settings)
    val updates = com.takeruf.nagi.updates.AppUpdates(application, scope)
    private val region = AndroidSearchRegion(application)
    val regionalSearch = RegionalSearchDefaults(workspace, settings, region::lookup, region::fallback)
    init { scope.launch { runCatching {
        if (isPrivate && initialSettings != null) {
            val initial = initialSettings.settings.first()
            settings.update { initial.copy(selectedSpaceId = "personal", automaticSearchRegion = false,
                restoreTabs = false, autoVideoPip = false, videoPopups = false) }
        }
        val initialEngines = if (isPrivate && privateEngineSource != null) {
            privateEngineSource.ready.await()
            privateEngineSource.dao.engines().map { it.model() }
        } else null
        workspace.initialize(initialEngines)
        if (!isPrivate) { tabs.archiveNow(); regionalSearch.refresh() }
    } } }
    fun closePrivateWorkspace() {
        check(isPrivate)
        scope.cancel()
        database.close()
    }
}
