package com.orbit.browser.domain.model

data class Space(
    val id: String,
    val name: String,
    val icon: String = "◉",
    val color: Long = 0xFF426B5A,
    val position: Int = 0,
    val activeTabId: String? = null,
)

data class BrowserTab(
    val id: String,
    val spaceId: String,
    val url: String = "about:blank",
    val title: String = "New tab",
    val faviconUrl: String? = null,
    val isPinned: Boolean = false,
    val position: Int = 0,
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null,
    val archivedAt: Long? = null,
)

data class SearchEngine(
    val id: String,
    val name: String,
    val keyword: String,
    val urlTemplate: String,
    val iconUrl: String? = null,
)

data class HistoryEntry(val id: Long = 0, val url: String, val title: String,
    val faviconUrl: String? = null, val visitedAt: Long = System.currentTimeMillis())

data class Bookmark(val id: String, val url: String, val title: String,
    val faviconUrl: String? = null, val spaceId: String? = null,
    val isFavorite: Boolean = false, val createdAt: Long = System.currentTimeMillis())

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class ArchivePeriod(val days: Int?) { NEVER(null), DAY(1), WEEK(7), MONTH(30) }

data class BrowserSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val sidebarWidth: Float = 264f,
    val sidebarCollapsed: Boolean = false,
    val defaultSearchEngineId: String = "google",
    val selectedSpaceId: String = "personal",
    val restoreTabs: Boolean = true,
    val desktopDefault: Boolean = false,
    val openLinksInNewTab: Boolean = false,
    val archivePeriod: ArchivePeriod = ArchivePeriod.NEVER,
    val automaticSearchRegion: Boolean = true,
    val searchRegionCountry: String? = null,
    val searchRegionSource: String? = null,
    val searchRegionCheckedAt: Long = 0,
)

data class WorkspaceSnapshot(
    val spaces: List<Space> = emptyList(), val tabs: List<BrowserTab> = emptyList(),
    val searchEngines: List<SearchEngine> = emptyList(),
    val history: List<HistoryEntry> = emptyList(), val bookmarks: List<Bookmark> = emptyList(),
)
