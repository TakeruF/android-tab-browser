package com.takeruf.nagi.data.room

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BrowserDao {
    @Query("SELECT * FROM spaces ORDER BY position") fun observeSpaces(): Flow<List<SpaceEntity>>
    @Query("SELECT * FROM tabs ORDER BY position, lastAccessedAt DESC") fun observeTabs(): Flow<List<TabEntity>>
    @Query("SELECT * FROM search_engines ORDER BY name COLLATE NOCASE") fun observeEngines(): Flow<List<SearchEngineEntity>>
    @Query("SELECT * FROM history ORDER BY visitedAt DESC, id DESC") fun observeHistory(): Flow<List<HistoryEntity>>
    @Query("SELECT * FROM history WHERE spaceId = :spaceId ORDER BY visitedAt DESC, id DESC LIMIT 2000")
    fun observeHistoryForSpace(spaceId: String): Flow<List<HistoryEntity>>
    @Query("SELECT * FROM bookmarks ORDER BY createdAt") fun observeBookmarks(): Flow<List<BookmarkEntity>>
    @Query("SELECT * FROM spaces ORDER BY position") suspend fun spaces(): List<SpaceEntity>
    @Query("SELECT * FROM tabs WHERE id = :id") suspend fun tab(id: String): TabEntity?
    @Query("SELECT * FROM tabs WHERE spaceId = :spaceId ORDER BY position") suspend fun tabs(spaceId: String): List<TabEntity>
    @Query("SELECT * FROM tabs WHERE closedAt IS NOT NULL ORDER BY closedAt DESC LIMIT 1") suspend fun lastClosed(): TabEntity?
    @Query("SELECT * FROM search_engines ORDER BY name") suspend fun engines(): List<SearchEngineEntity>
    @Upsert suspend fun putSpace(space: SpaceEntity)
    @Upsert suspend fun putTab(tab: TabEntity)
    @Upsert suspend fun putEngine(engine: SearchEngineEntity)
    @Upsert suspend fun putBookmark(bookmark: BookmarkEntity)
    @Query("UPDATE bookmarks SET title = :title, faviconUrl = CASE WHEN url = :url THEN faviconUrl ELSE NULL END, url = :url WHERE id = :id AND isFavorite = 1")
    suspend fun updateFavorite(id: String, title: String, url: String)
    @Insert suspend fun addHistory(entry: HistoryEntity): Long
    @Query("UPDATE history SET title = :title, faviconUrl = COALESCE(:favicon, faviconUrl) WHERE id = :id AND url = :url AND spaceId = :spaceId")
    suspend fun updateHistoryMetadata(id: Long, url: String, title: String, favicon: String?, spaceId: String): Int
    @Query("UPDATE tabs SET faviconUrl = CASE WHEN :favicon IS NOT NULL THEN :favicon WHEN url = :url THEN faviconUrl ELSE NULL END, url = :url, title = :title WHERE id = :id AND closedAt IS NULL")
    suspend fun updatePage(id: String, url: String, title: String, favicon: String?)
    @Query("UPDATE bookmarks SET faviconUrl = :favicon WHERE RTRIM(url, '/') = RTRIM(:url, '/')")
    suspend fun updateBookmarkFavicon(url: String, favicon: String)
    @Query("UPDATE tabs SET lastAccessedAt = :now WHERE id = :id") suspend fun touchTab(id: String, now: Long)
    @Query("DELETE FROM spaces WHERE id = :id") suspend fun deleteSpace(id: String)
    @Query("DELETE FROM search_engines WHERE id = :id") suspend fun deleteEngine(id: String)
    @Query("DELETE FROM bookmarks WHERE id = :id") suspend fun deleteBookmark(id: String)
    @Query("DELETE FROM history") suspend fun clearHistory()
    @Query("DELETE FROM history WHERE spaceId = :spaceId") suspend fun clearHistoryForSpace(spaceId: String)
    @Query("DELETE FROM history WHERE id = :id") suspend fun deleteHistory(id: Long)
    @Query("DELETE FROM history WHERE id = :id AND spaceId = :spaceId") suspend fun deleteHistoryForSpace(id: Long, spaceId: String)
    @Query("UPDATE history SET spaceId = :spaceId WHERE spaceId IS NULL") suspend fun assignUnownedHistory(spaceId: String)
    @Query("DELETE FROM tabs WHERE closedAt IS NOT NULL AND id NOT IN (SELECT id FROM tabs WHERE closedAt IS NOT NULL ORDER BY closedAt DESC LIMIT 30)")
    suspend fun trimClosedTabs()
    @Query("DELETE FROM history WHERE spaceId = :spaceId AND id NOT IN (SELECT id FROM history WHERE spaceId = :spaceId ORDER BY visitedAt DESC, id DESC LIMIT 5000)")
    suspend fun trimHistory(spaceId: String)
    @Query("UPDATE tabs SET archivedAt = :now WHERE closedAt IS NULL AND archivedAt IS NULL AND isPinned = 0 AND lastAccessedAt < :before AND id NOT IN (SELECT activeTabId FROM spaces WHERE activeTabId IS NOT NULL) AND id NOT IN (:protectedIds) AND splitRightTabId IS NULL AND id NOT IN (SELECT splitRightTabId FROM tabs WHERE splitRightTabId IS NOT NULL AND closedAt IS NULL)")
    suspend fun archiveInactive(before: Long, now: Long, protectedIds: List<String> = emptyList())
}
