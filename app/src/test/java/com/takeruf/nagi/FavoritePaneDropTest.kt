package com.takeruf.nagi

import android.app.Application
import androidx.room.Room
import com.takeruf.nagi.data.datastore.SettingsStore
import com.takeruf.nagi.data.repository.*
import com.takeruf.nagi.data.room.*
import com.takeruf.nagi.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FavoritePaneDropTest {
    private lateinit var db: NagiDatabase
    private lateinit var tabs: TabRepository
    private lateinit var left: String
    private lateinit var right: String
    private val favorite = Bookmark("pane-favorite", "https://favorite.test/page", "Favorite page",
        faviconUrl = "/favorite.png", isFavorite = true, createdAt = -25).entity()
    @Before fun setup() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, NagiDatabase::class.java).build()
        val settings = SettingsStore(context)
        settings.update { BrowserSettings(automaticSearchRegion = false) }
        val workspace = WorkspaceRepository(db, settings)
        workspace.initialize()
        tabs = TabRepository(workspace, settings)
        db.browserDao().putBookmark(favorite)
        left = tabs.create("personal", "https://left.test", select = false)
        right = tabs.create("personal", "https://right.test", select = false)
        tabs.pair(left, right)
        tabs.select(right)
    }
    @After fun cleanup() { db.close() }
    private suspend fun bookmarks() = db.browserDao().observeBookmarks().first()

    @Test fun existingNonfocusedPaneWinsWithoutChangingItsPartnerOrFavorite() = runBlocking {
        val saved = bookmarks()
        val other = db.browserDao().tab(right)!!
        val ids = db.browserDao().tabs("personal").map { it.id }
        val result = tabs.prepareFavoritePaneDrop(favorite.id, left)!!
        assertEquals(left, result.tab.id)
        assertEquals(favorite.url, result.url)
        assertEquals(left, db.browserDao().spaces().first { it.id == "personal" }.activeTabId)
        assertEquals(right, db.browserDao().tab(left)!!.splitRightTabId)
        assertEquals(other, db.browserDao().tab(right))
        assertEquals(ids, db.browserDao().tabs("personal").map { it.id })
        repeat(3) { assertEquals(right, tabs.prepareFavoritePaneDrop(favorite.id, right)!!.tab.id) }
        assertEquals(saved, bookmarks())
        assertEquals(ids, db.browserDao().tabs("personal").map { it.id })
    }

    @Test fun bothEdgesCreateAndSelectOneNewPaneWhileKeepingTheShortcut() = runBlocking {
        val saved = bookmarks()
        for (toRight in listOf(false, true)) {
            val anchor = tabs.create("personal", "https://anchor.test/$toRight", select = false)
            tabs.create("personal", "https://following.test/$toRight", select = false)
            val order = db.browserDao().tabs("personal").map { it.id }
            val before = db.browserDao().tab(anchor)!!
            val result = tabs.prepareFavoritePaneDrop(favorite.id, anchor, toRight)!!
            assertNotEquals(anchor, result.tab.id)
            assertEquals(favorite.url, result.tab.url)
            assertEquals(favorite.title, result.tab.title)
            assertEquals(favorite.faviconUrl, result.tab.faviconUrl)
            assertEquals(result.tab.id, db.browserDao().spaces().first { it.id == "personal" }.activeTabId)
            val pairLeft = if (toRight) anchor else result.tab.id
            val pairRight = if (toRight) result.tab.id else anchor
            assertEquals(pairRight, db.browserDao().tab(pairLeft)!!.splitRightTabId)
            val updatedOrder = db.browserDao().tabs("personal").map { it.id }
            assertEquals("Other tabs keep their relative positions", order, updatedOrder.filter { it != result.tab.id })
            assertEquals(updatedOrder.indexOf(pairLeft) + 1, updatedOrder.indexOf(pairRight))
            assertEquals(before.url, db.browserDao().tab(anchor)!!.url)
            assertEquals(saved, bookmarks())
        }
    }

    @Test fun staleOrInvalidDropsCannotCreateTabsOrDetachAnExistingPair() = runBlocking {
        val beforeTabs = db.browserDao().observeTabs().first()
        val beforeBookmarks = bookmarks()
        assertNull(tabs.prepareFavoritePaneDrop(favorite.id, left, true))
        assertNull(tabs.prepareFavoritePaneDrop("removed", left))
        val work = tabs.create("work", select = false)
        assertNull(tabs.prepareFavoritePaneDrop(favorite.id, work))
        tabs.close(work)
        assertNull(tabs.prepareFavoritePaneDrop(favorite.id, work))
        assertEquals(beforeTabs.filter { it.spaceId == "personal" },
            db.browserDao().observeTabs().first().filter { it.spaceId == "personal" })
        assertEquals(beforeBookmarks, bookmarks())
    }

    @Test fun concurrentEdgeDropsCannotLeaveAnOrphanOrSplitTheAnchorTwice() = runBlocking {
        val anchor = tabs.create("personal", "https://anchor.test", select = false)
        val count = db.browserDao().tabs("personal").size
        val results = coroutineScope {
            listOf(async(Dispatchers.IO) { tabs.prepareFavoritePaneDrop(favorite.id, anchor, true) },
                async(Dispatchers.IO) { tabs.prepareFavoritePaneDrop(favorite.id, anchor, false) }).awaitAll()
        }
        assertEquals(1, results.filterNotNull().size)
        assertEquals(count + 1, db.browserDao().tabs("personal").size)
        assertEquals(favorite, bookmarks().first { it.id == favorite.id })
    }
}
