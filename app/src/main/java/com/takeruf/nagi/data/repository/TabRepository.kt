package com.takeruf.nagi.data.repository

import androidx.room.withTransaction
import com.takeruf.nagi.data.datastore.SettingsStore
import com.takeruf.nagi.data.room.*
import com.takeruf.nagi.domain.model.*
import com.takeruf.nagi.browser.search.InputResolver
import kotlinx.coroutines.flow.first
import java.util.UUID

data class FavoritePaneDropResult(val tab: BrowserTab, val url: String)

class TabRepository(private val workspace: WorkspaceRepository, private val settings: SettingsStore) {
    private val dao = workspace.dao
    private val db = workspace.database
    @Volatile private var protectedTabIds: Set<String> = emptySet()
    fun protectFromArchive(ids: Set<String>) { protectedTabIds = ids.toSet() }

    suspend fun create(spaceId: String, url: String = "about:blank", select: Boolean = true, parentTabId: String? = null,
        id: String = UUID.randomUUID().toString()): String {
        workspace.ready.await()
        return db.withTransaction {
            val space = dao.spaces().firstOrNull { it.id == spaceId } ?: error("Space no longer exists")
            require(dao.tab(id) == null) { "Tab already exists" }
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
    /** Resolve a shared shortcut without moving it; create/pair/select a new pane atomically if requested. */
    suspend fun prepareFavoritePaneDrop(favoriteId: String, targetId: String,
        splitToRight: Boolean? = null): FavoritePaneDropResult? {
        workspace.ready.await()
        val selectedSpaceId = settings.settings.first().selectedSpaceId
        return db.withTransaction {
            val favorite = dao.observeBookmarks().first().firstOrNull { it.id == favoriteId && it.isFavorite }
                ?: return@withTransaction null
            val url = InputResolver.normalizeUrl(favorite.url) ?: return@withTransaction null
            val target = dao.tab(targetId)?.takeIf {
                it.closedAt == null && it.archivedAt == null && it.spaceId == selectedSpaceId
            } ?: return@withTransaction null
            val space = dao.spaces().firstOrNull { it.id == target.spaceId } ?: return@withTransaction null
            if (splitToRight == null) {
                dao.putSpace(space.copy(activeTabId = target.id))
                dao.touchTab(target.id, System.currentTimeMillis())
                FavoritePaneDropResult(target.model(), url)
            } else {
                // A stale edge target must not detach a pair created since the drag began.
                if (dao.tabs(space.id).any { it.splitRightTabId == target.id ||
                        it.id == target.id && it.splitRightTabId != null }) return@withTransaction null
                val id = create(space.id, url, select = false, parentTabId = target.id)
                val created = dao.tab(id)!!.copy(title = favorite.title, faviconUrl = favorite.faviconUrl,
                    isPinned = target.isPinned)
                dao.putTab(created)
                // Insert beside the anchor before pairing, so a new left member
                // does not move the pair's sidebar group to the end of the list.
                drop(id, target.isPinned, target.id, after = splitToRight)
                if (splitToRight) pair(target.id, id) else pair(id, target.id)
                dao.putSpace(space.copy(activeTabId = id))
                FavoritePaneDropResult(dao.tab(id)!!.model(), url)
            }
        }
    }
    suspend fun close(id: String, selectNext: Boolean = false) {
        closeInternal(id, selectNext)
    }
    suspend fun closePreviewedTab(id: String, expectedDestinationId: String): Boolean =
        closeInternal(id, selectNext = true, expectedDestinationId = expectedDestinationId)

    private suspend fun closeInternal(id: String, selectNext: Boolean, expectedDestinationId: String? = null): Boolean {
        workspace.ready.await()
        return db.withTransaction {
            val tab = dao.tab(id)?.takeIf { it.closedAt == null } ?: return@withTransaction false
            val next = TabCloseDestination.next(tab.model(), dao.tabs(tab.spaceId).map { it.model() })
            // A gesture must not commit to a different tab if its predicted destination vanished.
            if (expectedDestinationId != null && next?.id != expectedDestinationId) return@withTransaction false
            unpair(id)
            dao.putTab(dao.tab(id)!!.copy(closedAt = System.currentTimeMillis()))
            val space = dao.spaces().firstOrNull { it.id == tab.spaceId } ?: return@withTransaction true
            if (space.activeTabId == id || selectNext) {
                val nextId = next?.id ?: create(tab.spaceId, select = false)
                dao.putSpace(space.copy(activeTabId = nextId))
            }
            dao.trimClosedTabs()
            true
        }
    }
    suspend fun restoreClosed(): String? {
        workspace.ready.await()
        val prefs = settings.settings.first()
        val tab = db.withTransaction {
            val spaceId = dao.spaces().firstOrNull { it.id == prefs.selectedSpaceId }?.id ?: dao.spaces().firstOrNull()?.id
            val closed = (if (prefs.spacesEnabled) dao.lastClosed() else spaceId?.let { id ->
                dao.tabs(id).filter { it.closedAt != null }.maxByOrNull { it.closedAt!! }
            }) ?: return@withTransaction null
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
    private suspend fun unpair(id: String) {
        val tab = dao.tab(id) ?: return
        dao.tabs(tab.spaceId).filter { it.id == id || it.splitRightTabId == id }.forEach {
            if (it.splitRightTabId != null) dao.putTab(it.copy(splitRightTabId = null))
        }
    }
    suspend fun detachSplit(id: String) {
        workspace.ready.await()
        db.withTransaction { unpair(id) }
    }
    suspend fun pair(leftId: String, rightId: String) {
        workspace.ready.await()
        db.withTransaction {
            val left = dao.tab(leftId)?.takeIf { it.closedAt == null && it.archivedAt == null } ?: return@withTransaction
            val right = dao.tab(rightId)?.takeIf { it.closedAt == null && it.archivedAt == null } ?: return@withTransaction
            if (leftId == rightId || left.spaceId != right.spaceId) return@withTransaction
            unpair(leftId); unpair(rightId)
            dao.putTab(dao.tab(leftId)!!.copy(splitRightTabId = rightId))
            dao.putTab(dao.tab(rightId)!!.copy(isPinned = left.isPinned))
        }
    }
    /** Move both members atomically, retaining left/right identity and section. */
    suspend fun dropPair(leftId: String, pinned: Boolean, targetId: String? = null, after: Boolean = false) {
        workspace.ready.await()
        db.withTransaction {
            val left = dao.tab(leftId)?.takeIf { it.closedAt == null && it.archivedAt == null } ?: return@withTransaction
            val right = left.splitRightTabId?.let { dao.tab(it) }
                ?.takeIf { it.spaceId == left.spaceId && it.closedAt == null && it.archivedAt == null } ?: return@withTransaction
            if (targetId in listOf(left.id, right.id)) return@withTransaction
            val live = dao.tabs(left.spaceId).filter { it.closedAt == null && it.archivedAt == null && it.id !in listOf(left.id, right.id) }
            val section = live.filter { it.isPinned == pinned }.toMutableList()
            // An insertion beside either member of another pair must stay outside that pair.
            val anchor = targetId?.let { target -> section.firstOrNull { it.id == target || it.splitRightTabId == target } }
            val anchorId = if (after) anchor?.splitRightTabId ?: anchor?.id else anchor?.id
            val index = anchorId?.let { target -> section.indexOfFirst { it.id == target } }
            if (targetId != null && (index == null || index < 0)) return@withTransaction
            section.addAll(index?.let { it + if (after) 1 else 0 } ?: section.size,
                listOf(left.copy(isPinned = pinned), right.copy(isPinned = pinned)))
            val ordered = if (pinned) section + live.filter { !it.isPinned } else live.filter { it.isPinned } + section
            ordered.forEachIndexed { i, tab -> dao.putTab(tab.copy(position = i)) }
        }
    }
    suspend fun movePairToSpace(leftId: String, targetSpaceId: String) {
        workspace.ready.await()
        db.withTransaction {
            val left = dao.tab(leftId) ?: return@withTransaction
            val right = left.splitRightTabId?.let { dao.tab(it) } ?: return@withTransaction
            if (left.spaceId == targetSpaceId || dao.spaces().none { it.id == targetSpaceId }) return@withTransaction
            val oldSpace = dao.spaces().first { it.id == left.spaceId }
            val position = (dao.tabs(targetSpaceId).maxOfOrNull { it.position } ?: -1) + 1
            dao.putTab(left.copy(spaceId = targetSpaceId, position = position))
            dao.putTab(right.copy(spaceId = targetSpaceId, position = position + 1))
            if (oldSpace.activeTabId in listOf(left.id, right.id)) {
                val next = dao.tabs(oldSpace.id).firstOrNull { it.closedAt == null && it.archivedAt == null }
                dao.putSpace(oldSpace.copy(activeTabId = next?.id ?: create(oldSpace.id, select = false)))
            }
        }
    }
    suspend fun togglePin(id: String) {
        workspace.ready.await()
        db.withTransaction {
            val tab = dao.tab(id) ?: return@withTransaction
            val left = dao.tabs(tab.spaceId).firstOrNull { it.id == id && it.splitRightTabId != null || it.splitRightTabId == id }
            if (left != null) dropPair(left.id, !tab.isPinned)
            else dao.putTab(tab.copy(isPinned = !tab.isPinned))
        }
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
            val initial = dao.tabs(tab.spaceId).filter { it.closedAt == null && it.archivedAt == null && it.id != id }
            if (targetId != null && initial.none { it.id == targetId && it.isPinned == pinned }) return@withTransaction
            unpair(id)
            val moving = dao.tab(id)!!
            val live = dao.tabs(tab.spaceId).filter { it.closedAt == null && it.archivedAt == null && it.id != id }
            val section = live.filter { it.isPinned == pinned }.toMutableList()
            val anchor = targetId?.let { target -> section.firstOrNull { it.id == target || it.splitRightTabId == target } }
            val anchorId = if (after) anchor?.splitRightTabId ?: anchor?.id else anchor?.id
            val index = anchorId?.let { target -> section.indexOfFirst { it.id == target } }
            section.add(index?.let { it + if (after) 1 else 0 } ?: section.size, moving.copy(isPinned = pinned))
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
            unpair(id)
            val oldSpace = dao.spaces().first { it.id == tab.spaceId }
            dao.putTab(dao.tab(id)!!.copy(spaceId = targetSpaceId,
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
