package com.takeruf.nagi.data.repository

import androidx.room.withTransaction
import com.takeruf.nagi.browser.search.InputResolver
import com.takeruf.nagi.browser.search.AiSearchEngines
import com.takeruf.nagi.data.datastore.SettingsStore
import com.takeruf.nagi.data.room.*
import com.takeruf.nagi.domain.model.SearchEngine

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
        settings.update {
            val next = it.copy(commonSearchEngineIds = it.commonSearchEngineIds?.minus(id),
                defaultAiEngineId = if (AiSearchEngines.defaultId(it) == id) "chatgpt" else it.defaultAiEngineId)
            if (it.defaultSearchEngineId == id) next.copy(defaultSearchEngineId = (remaining.firstOrNull { engine -> engine.id !in AiSearchEngines.ids } ?: remaining.first()).id, automaticSearchRegion = false) else next
        }
    }
    suspend fun setDefault(id: String) {
        workspace.ready.await()
        require(id !in AiSearchEngines.ids && workspace.dao.engines().any { it.id == id })
        settings.update { it.copy(defaultSearchEngineId = id, automaticSearchRegion = false,
            commonSearchEngineIds = it.commonSearchEngineIds?.plus(id)) }
    }
    suspend fun setDefaultAi(id: String) {
        workspace.ready.await()
        require(id in AiSearchEngines.ids && (id == "chatgpt" || workspace.dao.engines().any { it.id == id }))
        settings.update { it.copy(defaultAiEngineId = id,
            commonSearchEngineIds = it.commonSearchEngineIds?.plus(id)) }
    }

}
