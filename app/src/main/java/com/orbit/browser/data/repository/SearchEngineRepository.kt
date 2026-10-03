package com.orbit.browser.data.repository

import androidx.room.withTransaction
import com.orbit.browser.browser.search.InputResolver
import com.orbit.browser.data.datastore.SettingsStore
import com.orbit.browser.data.room.*
import com.orbit.browser.domain.model.SearchEngine

class SearchEngineRepository(private val workspace: WorkspaceRepository, private val settings: SettingsStore) {
    suspend fun save(engine: SearchEngine) {
        InputResolver.validateEngine(engine)?.let { throw IllegalArgumentException(it) }
        workspace.ready.await()
        workspace.database.withTransaction {
            require(workspace.dao.engines().none { it.id != engine.id && it.keyword == engine.keyword }) {
                "That keyword is already in use"
            }
            workspace.dao.putEngine(engine.entity())
        }
    }
    suspend fun delete(id: String) {
        workspace.ready.await()
        val remaining = workspace.database.withTransaction {
            require(workspace.dao.engines().size > 1) { "Keep at least one search engine" }
            workspace.dao.deleteEngine(id); workspace.dao.engines()
        }
        settings.update { if (it.defaultSearchEngineId == id) it.copy(defaultSearchEngineId = remaining.first().id, automaticSearchRegion = false) else it }
    }
    suspend fun setDefault(id: String) {
        workspace.ready.await()
        require(workspace.dao.engines().any { it.id == id })
        settings.update { it.copy(defaultSearchEngineId = id, automaticSearchRegion = false) }
    }
}
