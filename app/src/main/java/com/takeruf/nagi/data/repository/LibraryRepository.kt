package com.takeruf.nagi.data.repository

import androidx.room.withTransaction
import com.takeruf.nagi.data.room.*
import com.takeruf.nagi.domain.model.*
import java.util.UUID
import kotlinx.coroutines.flow.first

class LibraryRepository(private val workspace: WorkspaceRepository) {
    private val dao = workspace.dao
    suspend fun recordVisit(url: String, title: String, favicon: String?) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        workspace.ready.await()
        workspace.database.withTransaction {
            dao.addHistory(HistoryEntry(url = url, title = title, faviconUrl = favicon).entity())
            dao.trimHistory()
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

    suspend fun removeBookmark(id: String) { workspace.ready.await(); dao.deleteBookmark(id) }
    suspend fun clearHistory() { workspace.ready.await(); dao.clearHistory() }
    suspend fun deleteHistory(id: Long) { workspace.ready.await(); dao.deleteHistory(id) }
}
