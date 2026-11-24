package com.takeruf.nagi.data.repository

import androidx.room.withTransaction
import com.takeruf.nagi.data.room.*
import com.takeruf.nagi.domain.model.*
import com.takeruf.nagi.browser.search.InputResolver
import java.util.UUID
import kotlinx.coroutines.flow.first

class LibraryRepository(private val workspace: WorkspaceRepository) {
    private val dao = workspace.dao
    // Application-owned, so configuration changes cannot turn the currently displayed
    // document into another visit. Access is serialized by Room transactions.
    private data class LastVisit(val url: String, val id: Long, val spaceId: String)
    private val lastVisitByTab = mutableMapOf<String, LastVisit>()
    suspend fun recordVisit(url: String, title: String, favicon: String?, tabId: String) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        workspace.ready.await()
        workspace.database.withTransaction {
            // Use the callback's tab, never the currently selected Space: background
            // panes can finish after a Space switch, move or deletion.
            val tab = dao.tab(tabId)?.takeIf { it.closedAt == null } ?: return@withTransaction
            val spaceId = tab.spaceId
            val previous = lastVisitByTab[tabId]
            if (previous?.url == url && previous.spaceId == spaceId &&
                dao.updateHistoryMetadata(previous.id, url, title, favicon, spaceId) > 0) {
                return@withTransaction
            }
            val id = dao.addHistory(HistoryEntry(url = url, title = title, faviconUrl = favicon, spaceId = spaceId).entity())
            lastVisitByTab[tabId] = LastVisit(url, id, spaceId)
            dao.trimHistory(spaceId)
        }
    }
    suspend fun addBookmark(tab: BrowserTab, favorite: Boolean) {
        if (!tab.url.startsWith("http")) return
        workspace.ready.await()
        workspace.database.withTransaction {
            if (dao.observeBookmarks().first().any { it.url == tab.url && it.isFavorite == favorite }) return@withTransaction
            if (favorite) {
                val favorites = dao.observeBookmarks().first().filter { it.isFavorite }
                if (favorites.any { it.url == tab.url }) return@withTransaction
                require(favorites.size < 12) { "Keep up to 12 favorites" }
            }
            dao.putBookmark(Bookmark(UUID.randomUUID().toString(), tab.url, tab.title, tab.faviconUrl,
                null, favorite).entity())
        }
    }

    suspend fun editFavorite(id: String, title: String, url: String) {
        require(title.isNotBlank()) { "Name is required" }
        val normalized = requireNotNull(InputResolver.normalizeUrl(url.trim())) { "Enter a valid http or https URL" }
        workspace.ready.await()
        workspace.database.withTransaction {
            val favorites = dao.observeBookmarks().first().filter { it.isFavorite }
            if (favorites.none { it.id == id }) return@withTransaction
            require(favorites.none { it.id != id && InputResolver.normalizeUrl(it.url) == normalized }) {
                "This URL is already in Favorites"
            }
            // Update only editable fields so ordering, identity and concurrent favicon
            // updates are preserved. A deleted entry is never inserted again.
            dao.updateFavorite(id, title.trim(), normalized)
        }
    }

    suspend fun removeBookmark(id: String) { workspace.ready.await(); dao.deleteBookmark(id) }
    suspend fun clearHistory() {
        workspace.ready.await()
        workspace.database.withTransaction { dao.clearHistory(); lastVisitByTab.clear() }
    }
    suspend fun clearHistoryForSpace(spaceId: String) {
        workspace.ready.await()
        workspace.database.withTransaction {
            dao.clearHistoryForSpace(spaceId)
            lastVisitByTab.entries.removeAll { it.value.spaceId == spaceId }
        }
    }
    suspend fun deleteHistory(id: Long, spaceId: String? = null) {
        workspace.ready.await()
        workspace.database.withTransaction {
            if (spaceId == null) dao.deleteHistory(id) else dao.deleteHistoryForSpace(id, spaceId)
            lastVisitByTab.entries.removeAll { it.value.id == id && (spaceId == null || it.value.spaceId == spaceId) }
        }
    }
}
