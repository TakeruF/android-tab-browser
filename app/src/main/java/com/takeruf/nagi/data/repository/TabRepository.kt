package com.takeruf.nagi.data.repository

import androidx.room.withTransaction
import com.takeruf.nagi.data.datastore.SettingsStore
import com.takeruf.nagi.data.room.*
import com.takeruf.nagi.domain.model.*
import kotlinx.coroutines.flow.first
import java.util.UUID

class TabRepository(private val workspace: WorkspaceRepository, private val settings: SettingsStore) {
    private val dao = workspace.dao
    private val db = workspace.database
    @Volatile private var protectedTabIds: Set<String> = emptySet()
    fun protectFromArchive(ids: Set<String>) { protectedTabIds = ids.toSet() }

    suspend fun create(spaceId: String, url: String = "about:blank", select: Boolean = true, parentTabId: String? = null): String {
        workspace.ready.await()
        return db.withTransaction {
            val space = dao.spaces().firstOrNull { it.id == spaceId } ?: error("Space no longer exists")
            val id = UUID.randomUUID().toString()
            val position = (dao.tabs(spaceId).maxOfOrNull { it.position } ?: -1) + 1
            dao.putTab(BrowserTab(id, spaceId, url, if (url == "about:blank") "New tab" else url,
                position = position, parentTabId = (parentTabId ?: space.activeTabId.takeIf { select })
                    ?.takeIf { parent -> dao.tab(parent)?.let { it.spaceId == spaceId && it.closedAt == null } == true }).entity())
            if (select) dao.putSpace(space.copy(activeTabId = id))
            id
        }
    }
    suspend fun select(id: String) {
        workspace.ready.await()
        val spaceId = db.withTransaction {
            val tab = dao.tab(id) ?: return@withTransaction null
            if (tab.closedAt != null) return@withTransaction null
            if (tab.archivedAt != null) dao.putTab(tab.copy(archivedAt = null))
            val space = dao.spaces().firstOrNull { it.id == tab.spaceId } ?: return@withTransaction null
            dao.putSpace(space.copy(activeTabId = id)); dao.touchTab(id, System.currentTimeMillis())
            tab.spaceId
        }
        if (spaceId != null) settings.update { it.copy(selectedSpaceId = spaceId) }
    }
    suspend fun close(id: String, selectNext: Boolean = false) {
        workspace.ready.await()
        db.withTransaction {
            val tab = dao.tab(id)?.takeIf { it.closedAt == null } ?: return@withTransaction
            dao.putTab(tab.copy(closedAt = System.currentTimeMillis()))
            val space = dao.spaces().firstOrNull { it.id == tab.spaceId } ?: return@withTransaction
            if (space.activeTabId == id || selectNext) {
                val remaining = dao.tabs(tab.spaceId).filter { it.closedAt == null && it.archivedAt == null }
                val next = remaining.firstOrNull { it.id == tab.parentTabId }
                    ?: remaining.minByOrNull { kotlin.math.abs(it.position - tab.position) }
                val nextId = next?.id ?: create(tab.spaceId, select = false)
                dao.putSpace(space.copy(activeTabId = nextId))
            }
            dao.trimClosedTabs()
        }
    }
    suspend fun restoreClosed(): String? {
        workspace.ready.await()
        val tab = db.withTransaction {
            val closed = dao.lastClosed() ?: return@withTransaction null
            dao.putTab(closed.copy(closedAt = null, archivedAt = null, lastAccessedAt = System.currentTimeMillis()))
            closed.id
        }
        if (tab != null) select(tab)
        return tab
    }
    suspend fun updatePage(id: String, url: String, title: String, favicon: String?) {
        workspace.ready.await()
        db.withTransaction {
            dao.updatePage(id, url, title.ifBlank { url }, favicon)
            if (favicon != null) dao.updateBookmarkFavicon(url, favicon)
        }
    }
    suspend fun togglePin(id: String) {
        workspace.ready.await()
        db.withTransaction { dao.tab(id)?.let { dao.putTab(it.copy(isPinned = !it.isPinned)) } }
    }
    suspend fun recordAccess(id: String) {
        workspace.ready.await(); dao.touchTab(id, System.currentTimeMillis())
    }
    suspend fun reorder(id: String, targetId: String) {
        workspace.ready.await()
        db.withTransaction {
            val tab = dao.tab(id) ?: return@withTransaction
            val ordered = dao.tabs(tab.spaceId).filter { it.closedAt == null && it.archivedAt == null }.toMutableList()
            val target = ordered.indexOfFirst { it.id == targetId }
            if (target < 0 || id == targetId) return@withTransaction
            ordered.removeAll { it.id == id }; ordered.add(target.coerceAtMost(ordered.size), tab)
            ordered.forEachIndexed { index, value -> dao.putTab(value.copy(position = index)) }
        }
    }
    /** Explicit insertion side, calculated after removing the source, also changes pin state. */
    suspend fun drop(id: String, pinned: Boolean, targetId: String? = null, after: Boolean = false) {
        workspace.ready.await()
        db.withTransaction {
            val tab = dao.tab(id)?.takeIf { it.closedAt == null && it.archivedAt == null } ?: return@withTransaction
            if (targetId == id) return@withTransaction
            val live = dao.tabs(tab.spaceId).filter { it.closedAt == null && it.archivedAt == null && it.id != id }
            val section = live.filter { it.isPinned == pinned }.toMutableList()
            val index = targetId?.let { target -> section.indexOfFirst { it.id == target } }
            if (targetId != null && (index == null || index < 0)) return@withTransaction
            section.add(index?.let { it + if (after) 1 else 0 } ?: section.size, tab.copy(isPinned = pinned))
            val ordered = if (pinned) section + live.filter { !it.isPinned } else live.filter { it.isPinned } + section
            ordered.forEachIndexed { position, value -> dao.putTab(value.copy(position = position)) }
        }
    }
    suspend fun moveToFavorite(id: String, target: String? = null, after: Boolean = false) {
        workspace.ready.await()
        db.withTransaction {
            val tab = dao.tab(id)?.takeIf { it.closedAt == null && it.archivedAt == null && it.url.startsWith("http") }
                ?: return@withTransaction
            val favorites = dao.observeBookmarks().first().filter { it.isFavorite }
            val existing = favorites.firstOrNull { it.url == tab.url }
            require(existing != null || favorites.size < 12) { "Keep up to 12 favorites" }
            val bookmark = existing ?: Bookmark(UUID.randomUUID().toString(), tab.url, tab.title, tab.faviconUrl, isFavorite = true).entity()
            dao.putBookmark(bookmark)
            reorderFavorite(bookmark.id, target, after)
            close(id)
        }
    }
    suspend fun dropFavorite(id: String, spaceId: String, pinned: Boolean, target: String?, after: Boolean) {
        workspace.ready.await()
        db.withTransaction {
            val bookmark = dao.observeBookmarks().first().firstOrNull { it.id == id && it.isFavorite } ?: return@withTransaction
            if (target != null && dao.tabs(spaceId).none { it.id == target && it.isPinned == pinned && it.closedAt == null && it.archivedAt == null }) return@withTransaction
            val tabId = create(spaceId, bookmark.url, select = false)
            dao.putTab(dao.tab(tabId)!!.copy(title = bookmark.title, faviconUrl = bookmark.faviconUrl))
            drop(tabId, pinned, target, after)
            dao.deleteBookmark(id)
        }
    }
    suspend fun reorderFavorite(id: String, target: String?, after: Boolean) {
        workspace.ready.await()
        db.withTransaction {
            if (id == target) return@withTransaction
            val favorites = dao.observeBookmarks().first().filter { it.isFavorite }.toMutableList()
            val bookmark = favorites.firstOrNull { it.id == id } ?: return@withTransaction
            favorites.removeAll { it.id == id }
            val index = target?.let { t -> favorites.indexOfFirst { it.id == t } }
            if (target != null && (index == null || index < 0)) return@withTransaction
            favorites.add(index?.let { it + if (after) 1 else 0 } ?: favorites.size, bookmark)
            favorites.forEachIndexed { i, value -> dao.putBookmark(value.copy(createdAt = i.toLong(), spaceId = null)) }
        }
    }
    suspend fun moveToSpace(id: String, targetSpaceId: String) {
        workspace.ready.await()
        db.withTransaction {
            val tab = dao.tab(id) ?: return@withTransaction
            if (tab.spaceId == targetSpaceId || dao.spaces().none { it.id == targetSpaceId }) return@withTransaction
            val oldSpace = dao.spaces().first { it.id == tab.spaceId }
            dao.putTab(tab.copy(spaceId = targetSpaceId,
                position = (dao.tabs(targetSpaceId).maxOfOrNull { it.position } ?: -1) + 1))
            if (oldSpace.activeTabId == id) {
                val next = dao.tabs(oldSpace.id).firstOrNull { it.closedAt == null && it.archivedAt == null }
                dao.putSpace(oldSpace.copy(activeTabId = next?.id ?: create(oldSpace.id, select = false)))
            }
        }
    }
    suspend fun archiveNow() {
        workspace.ready.await()
        val days = settings.settings.first().archivePeriod.days ?: return
        val now = System.currentTimeMillis()
        dao.archiveInactive(now - days * 86_400_000L, now, protectedTabIds.toList())
    }
}
