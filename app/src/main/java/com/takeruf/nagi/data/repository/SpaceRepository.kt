package com.takeruf.nagi.data.repository

import androidx.room.withTransaction
import com.takeruf.nagi.data.datastore.SettingsStore
import com.takeruf.nagi.data.room.*
import com.takeruf.nagi.domain.model.Space
import java.util.UUID

class SpaceRepository(private val workspace: WorkspaceRepository, private val settings: SettingsStore,
    private val tabs: TabRepository) {
    private val dao = workspace.dao
    suspend fun create(name: String, icon: String = "◉", color: Long? = null) {
        require(name.trim().isNotEmpty()) { "Space name is required" }
        workspace.ready.await()
        val id = UUID.randomUUID().toString()
        workspace.database.withTransaction {
            val count = dao.spaces().size
            val colors = listOf(0xFF426B5A, 0xFF6C6193, 0xFFB07D47, 0xFF477F96)
            dao.putSpace(Space(id, name.trim(), icon, color ?: colors[count % colors.size], count).entity())
            tabs.create(id)
        }
        select(id)
    }
    suspend fun rename(id: String, name: String) {
        require(name.trim().isNotEmpty()) { "Space name is required" }
        workspace.ready.await()
        workspace.database.withTransaction {
            dao.spaces().firstOrNull { it.id == id }?.let { dao.putSpace(it.copy(name = name.trim())) }
        }
    }
    suspend fun edit(id: String, name: String, icon: String, color: Long) {
        require(name.trim().isNotEmpty()) { "Space name is required" }
        workspace.ready.await()
        workspace.database.withTransaction {
            dao.spaces().firstOrNull { it.id == id }?.let { dao.putSpace(it.copy(name = name.trim(), icon = icon, color = color)) }
        }
    }
    suspend fun select(id: String) {
        workspace.ready.await()
        if (dao.spaces().any { it.id == id }) settings.update { it.copy(selectedSpaceId = id) }
    }
    suspend fun delete(id: String) {
        workspace.ready.await()
        val remaining = workspace.database.withTransaction {
            val spaces = dao.spaces()
            require(spaces.size > 1) { "Keep at least one Space" }
            dao.deleteSpace(id)
            dao.spaces()
        }
        settings.update { if (it.selectedSpaceId == id) it.copy(selectedSpaceId = remaining.first().id) else it }
    }
}
