package com.takeruf.nagi.data.room

import androidx.room.*
import com.takeruf.nagi.domain.model.*

@Entity(tableName = "spaces")
data class SpaceEntity(@PrimaryKey val id: String, val name: String, val icon: String,
    val color: Long, val position: Int, val activeTabId: String?)

@Entity(tableName = "tabs", foreignKeys = [ForeignKey(entity = SpaceEntity::class,
    parentColumns = ["id"], childColumns = ["spaceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("spaceId"), Index("closedAt"), Index("lastAccessedAt")])
data class TabEntity(@PrimaryKey val id: String, val spaceId: String, val url: String,
    val title: String, val faviconUrl: String?, val isPinned: Boolean, val position: Int,
    val lastAccessedAt: Long, val closedAt: Long?, val archivedAt: Long?, val parentTabId: String? = null)

@Entity(tableName = "search_engines", indices = [Index(value = ["keyword"], unique = true)])
data class SearchEngineEntity(@PrimaryKey val id: String, val name: String, val keyword: String,
    val urlTemplate: String, val iconUrl: String?)

@Entity(tableName = "history", indices = [Index("url"), Index("visitedAt")])
data class HistoryEntity(@PrimaryKey(autoGenerate = true) val id: Long, val url: String,
    val title: String, val faviconUrl: String?, val visitedAt: Long)

@Entity(tableName = "bookmarks", foreignKeys = [ForeignKey(entity = SpaceEntity::class,
    parentColumns = ["id"], childColumns = ["spaceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("spaceId"), Index("url")])
data class BookmarkEntity(@PrimaryKey val id: String, val url: String, val title: String,
    val faviconUrl: String?, val spaceId: String?, val isFavorite: Boolean, val createdAt: Long)

fun SpaceEntity.model() = Space(id, name, icon, color, position, activeTabId)
fun Space.entity() = SpaceEntity(id, name, icon, color, position, activeTabId)
fun TabEntity.model() = BrowserTab(id, spaceId, url, title, faviconUrl, isPinned, position, lastAccessedAt, closedAt, archivedAt, parentTabId)
fun BrowserTab.entity() = TabEntity(id, spaceId, url, title, faviconUrl, isPinned, position, lastAccessedAt, closedAt, archivedAt, parentTabId)
fun SearchEngineEntity.model() = SearchEngine(id, name, keyword, urlTemplate, iconUrl)
fun SearchEngine.entity() = SearchEngineEntity(id, name, keyword, urlTemplate, iconUrl)
fun HistoryEntity.model() = HistoryEntry(id, url, title, faviconUrl, visitedAt)
fun HistoryEntry.entity() = HistoryEntity(id, url, title, faviconUrl, visitedAt)
fun BookmarkEntity.model() = Bookmark(id, url, title, faviconUrl, spaceId, isFavorite, createdAt)
fun Bookmark.entity() = BookmarkEntity(id, url, title, faviconUrl, spaceId, isFavorite, createdAt)
