package com.takeruf.nagi.data.repository

import androidx.room.withTransaction
import com.takeruf.nagi.browser.search.DefaultSearchEngines
import com.takeruf.nagi.browser.search.SearchEngineOrder
import com.takeruf.nagi.data.datastore.SettingsStore
import com.takeruf.nagi.data.room.*
import com.takeruf.nagi.domain.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.*
import java.util.UUID

class WorkspaceRepository(val database: NagiDatabase, private val settings: SettingsStore) {
    val dao = database.browserDao()
    val ready = CompletableDeferred<Unit>()
    val snapshot = combine(dao.observeSpaces(), dao.observeTabs(), dao.observeEngines(),
        dao.observeHistory(), dao.observeBookmarks()) { spaces, tabs, engines, history, bookmarks ->
        WorkspaceSnapshot(spaces.map { it.model() }, tabs.map { it.model() },
            SearchEngineOrder.sorted(engines.map { it.model() }) { it.id }, history.map { it.model() }, bookmarks.map { it.model() })
    }

    suspend fun initialize() {
        try {
            database.withTransaction {
                if (dao.spaces().isEmpty()) {
                    dao.putSpace(Space("personal", "Personal", "lucide:user-round").entity())
                    dao.putSpace(Space("work", "Work", "lucide:briefcase", 0xFF6C6193, 1).entity())
                    DefaultSearchEngines.all.forEach { dao.putEngine(it.entity()) }
                    listOf("Gmail" to "https://mail.google.com", "ChatGPT" to "https://chatgpt.com",
                        "GitHub" to "https://github.com").forEach { (name, url) ->
                        dao.putBookmark(Bookmark(UUID.randomUUID().toString(), url, name,
                            spaceId = null, isFavorite = true).entity())
                    }
                }
                // Upgrade only the legacy built-in defaults; preserve customized icons.
                dao.spaces().forEach { space ->
                    val icon = when {
                        space.id == "personal" && space.icon == "◉" -> "lucide:user-round"
                        space.id == "work" && space.icon == "▣" -> "lucide:briefcase"
                        else -> space.icon
                    }
                    if (icon != space.icon) dao.putSpace(space.copy(icon = icon))
                }
                // A one-time additive seed; later deletion remains respected.
                if (!settings.regionalEngineSeeded() && dao.engines().none { it.id == "baidu" || it.keyword == "bd" }) {
                    dao.putEngine(DefaultSearchEngines.all.first { it.id == "baidu" }.entity())
                }
                if (!settings.additionalEnginesSeeded()) {
                    DefaultSearchEngines.all.filter { it.id in DefaultSearchEngines.additionalIds }.forEach { engine ->
                        if (dao.engines().none { it.id == engine.id || it.keyword == engine.keyword }) dao.putEngine(engine.entity())
                    }
                }
                if (!settings.aiEnginesSeeded()) {
                    DefaultSearchEngines.all.filter { it.id in DefaultSearchEngines.aiIds }.forEach { engine ->
                        if (dao.engines().none { it.id == engine.id || it.keyword == engine.keyword }) dao.putEngine(engine.entity())
                    }
                }
                dao.observeBookmarks().first().filter { it.isFavorite && it.spaceId != null }.forEach {
                    dao.putBookmark(it.copy(spaceId = null))
                }
                // Engine deletion/editing is never undone by seeding on a later launch.
                val prefs = settings.settings.first()
                dao.spaces().forEach { space ->
                    val tabs = dao.tabs(space.id)
                    if (!prefs.restoreTabs) {
                        tabs.filter { it.closedAt == null && !it.isPinned }.forEach {
                            dao.putTab(it.copy(closedAt = System.currentTimeMillis()))
                        }
                    }
                    val live = dao.tabs(space.id).filter { it.closedAt == null && it.archivedAt == null }
                    val active = live.firstOrNull { it.id == space.activeTabId } ?: live.firstOrNull()
                    if (active == null) {
                        val tab = BrowserTab(UUID.randomUUID().toString(), space.id)
                        dao.putTab(tab.entity()); dao.putSpace(space.copy(activeTabId = tab.id))
                    } else dao.putSpace(space.copy(activeTabId = active.id))
                }
                dao.trimClosedTabs()
            }
            settings.markAiEnginesSeeded()
            settings.markAdditionalEnginesSeeded()
            settings.markRegionalEngineSeeded()
            val spaces = dao.spaces()
            settings.update { if (spaces.none { s -> s.id == it.selectedSpaceId })
                it.copy(selectedSpaceId = spaces.first().id) else it }
            ready.complete(Unit)
        } catch (error: Throwable) { ready.completeExceptionally(error); throw error }
    }
}
