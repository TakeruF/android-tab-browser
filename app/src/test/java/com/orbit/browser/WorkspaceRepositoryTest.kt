package com.orbit.browser

import android.app.Application
import androidx.room.Room
import com.orbit.browser.data.datastore.SettingsStore
import com.orbit.browser.data.repository.*
import com.orbit.browser.data.room.*
import com.orbit.browser.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WorkspaceRepositoryTest {
    private lateinit var db: OrbitDatabase
    private lateinit var settings: SettingsStore
    private lateinit var workspace: WorkspaceRepository
    private lateinit var tabs: TabRepository
    private lateinit var spaces: SpaceRepository
    @Before fun setup() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, OrbitDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsStore(context); settings.update { BrowserSettings() }
        workspace = WorkspaceRepository(db, settings); tabs = TabRepository(workspace, settings)
        spaces = SpaceRepository(workspace, settings, tabs); workspace.initialize()
    }
    @After fun close() { db.close() }
    @Test fun accentSurvivesIndependentSettingsUpdatesAndStoreRecreation() = runBlocking {
        settings.update { it.copy(accentColor = 0xFF3568C0) }
        settings.update { it.copy(theme = ThemeMode.DARK, sidebarWidth = 310f, sidebarCollapsed = true) }
        val saved = SettingsStore(RuntimeEnvironment.getApplication()).settings.first()
        assertEquals(0xFF3568C0, saved.accentColor)
        assertEquals(310f, saved.sidebarWidth)
        assertTrue(saved.sidebarCollapsed)
        assertEquals(ThemeMode.DARK, saved.theme)
    }
    @Test fun seedsEachSpaceWithOneSelectedTabAndEditableEngines() = runBlocking {
        assertEquals(2, db.browserDao().spaces().size)
        assertEquals(com.orbit.browser.browser.search.DefaultSearchEngines.all.size, db.browserDao().engines().size)
        db.browserDao().spaces().forEach { assertNotNull(db.browserDao().tab(it.activeTabId!!)) }
        val engine = db.browserDao().engines().first()
        db.browserDao().putEngine(engine.copy(name = "Edited"))
        workspace.initialize()
        assertEquals("Edited", db.browserDao().engines().first { it.id == engine.id }.name)
    }
    @Test fun closeLastTabCreatesReplacementAndRestoreKeepsSpace() = runBlocking {
        val id = db.browserDao().spaces().first().activeTabId!!
        tabs.updatePage(id, "https://example.com", "Example", null)
        tabs.close(id)
        assertNotNull(db.browserDao().tab(id)!!.closedAt)
        val replacement = db.browserDao().spaces().first().activeTabId!!
        assertNotEquals(id, replacement)
        assertEquals(id, tabs.restoreClosed())
        assertNull(db.browserDao().tab(id)!!.closedAt)
        assertEquals("https://example.com", db.browserDao().tab(id)!!.url)
    }
    @Test fun reorderAndMoveArePersistentAndRepairOldSpaceSelection() = runBlocking {
        val first = db.browserDao().spaces().first().activeTabId!!
        val second = tabs.create("personal", "https://second.com")
        tabs.reorder(second, first)
        assertEquals(second, db.browserDao().tabs("personal").first { it.closedAt == null }.id)
        tabs.moveToSpace(second, "work")
        assertEquals("work", db.browserDao().tab(second)!!.spaceId)
        assertNotEquals(second, db.browserDao().spaces().first { it.id == "personal" }.activeTabId)
    }
    @Test fun deletingSpaceCascadesButNeverDeletesFinalSpace() = runBlocking {
        val id = db.browserDao().spaces().first().activeTabId!!
        spaces.delete("personal")
        assertNull(db.browserDao().tab(id))
        assertTrue(db.browserDao().observeBookmarks().first().none { it.spaceId == "personal" })
        assertEquals("work", settings.settings.first().selectedSpaceId)
        try { spaces.delete("work"); fail("Expected final-space protection") } catch (_: IllegalArgumentException) { }
    }
    @Test fun launchWithoutRestorePreservesPinnedTabsAndClosesNormalTabs() = runBlocking {
        val pinned = db.browserDao().spaces().first().activeTabId!!
        tabs.togglePin(pinned)
        val normal = tabs.create("personal", "https://normal.com")
        settings.update { it.copy(restoreTabs = false) }
        workspace.initialize()
        assertNull(db.browserDao().tab(pinned)!!.closedAt)
        assertNotNull(db.browserDao().tab(normal)!!.closedAt)
        assertEquals(pinned, db.browserDao().spaces().first().activeTabId)
    }
    @Test fun archiveNeverHidesPinnedOrSelectedTabs() = runBlocking {
        val selected = db.browserDao().spaces().first().activeTabId!!
        val pinned = tabs.create("personal", select = false); tabs.togglePin(pinned)
        val old = tabs.create("personal", select = false)
        listOf(selected, pinned, old).forEach { db.browserDao().putTab(db.browserDao().tab(it)!!.copy(lastAccessedAt = 1)) }
        settings.update { it.copy(archivePeriod = ArchivePeriod.DAY) }; tabs.archiveNow()
        assertNull(db.browserDao().tab(selected)!!.archivedAt); assertNull(db.browserDao().tab(pinned)!!.archivedAt)
        assertNotNull(db.browserDao().tab(old)!!.archivedAt)
        tabs.select(old); assertNull(db.browserDao().tab(old)!!.archivedAt)
    }
    @Test fun deletingDefaultEngineSelectsRemainingEngineAndRejectsDuplicateKeywords() = runBlocking {
        val engines = SearchEngineRepository(workspace, settings)
        engines.delete("google")
        assertNotEquals("google", settings.settings.first().defaultSearchEngineId)
        try { engines.save(SearchEngine("custom", "Test", "ddg", "https://example.com/?q={query}")); fail("Expected uniqueness validation") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun favoritesAreGlobalAndSeparateFromBookmarks() = runBlocking {
        val library = LibraryRepository(workspace)
        val tab = BrowserTab("sample", "work", "https://example.com", "Example")
        library.addBookmark(tab, true); library.addBookmark(tab, false)
        val saved = db.browserDao().observeBookmarks().first().filter { it.url == tab.url }
        assertEquals(2, saved.size)
        assertNull(saved.first { it.isFavorite }.spaceId)
        assertNull(saved.first { !it.isFavorite }.spaceId)
    }
    @Test fun dropChangesSectionAndUsesExplicitInsertionSide() = runBlocking {
        val a = db.browserDao().spaces().first().activeTabId!!
        val b = tabs.create("personal", "https://b.com", select = false)
        val c = tabs.create("personal", "https://c.com", select = false)
        tabs.drop(a, false, c, after = true)
        assertEquals(listOf(b, c, a), db.browserDao().tabs("personal").filter { it.closedAt == null }.map { it.id })
        tabs.drop(c, true)
        tabs.drop(a, true, c, after = false)
        assertEquals(listOf(a, c), db.browserDao().tabs("personal").filter { it.isPinned }.map { it.id })
        tabs.drop(a, false, b, after = false)
        assertFalse(db.browserDao().tab(a)!!.isPinned)
        assertEquals(listOf(a, b), db.browserDao().tabs("personal").filter { !it.isPinned && it.closedAt == null }.map { it.id })
        val before = db.browserDao().tabs("personal")
        tabs.drop(a, false, "deleted-target")
        assertEquals(before, db.browserDao().tabs("personal"))
    }
    @Test fun favoriteDragRoundTripIsPersistentAndShared() = runBlocking {
        val a = tabs.create("personal", "https://a.com")
        tabs.updatePage(a, "https://a.com", "Alpha", null)
        tabs.moveToFavorite(a)
        assertNotNull(db.browserDao().tab(a)!!.closedAt)
        val favorite = db.browserDao().observeBookmarks().first().first { it.title == "Alpha" }
        assertNull(favorite.spaceId)
        val first = db.browserDao().observeBookmarks().first().first { it.isFavorite }.id
        tabs.reorderFavorite(favorite.id, first, false)
        assertEquals(favorite.id, db.browserDao().observeBookmarks().first().first().id)
        tabs.dropFavorite(favorite.id, "work", true, null, false)
        val restored = db.browserDao().tabs("work").first { it.url == "https://a.com" }
        assertTrue(restored.isPinned); assertEquals("Alpha", restored.title)
        assertTrue(db.browserDao().observeBookmarks().first().none { it.id == favorite.id })
    }
    @Test fun reloadingPinnedTabKeepsIconUntilNewIconArrivesButNavigationClearsIt() = runBlocking {
        val id = tabs.create("personal", "https://example.com")
        tabs.togglePin(id)
        tabs.updatePage(id, "https://example.com", "Example", "/saved/icon.png")
        tabs.updatePage(id, "https://example.com", "Reloading", null)
        assertEquals("/saved/icon.png", db.browserDao().tab(id)!!.faviconUrl)
        tabs.updatePage(id, "https://other.example", "Other", null)
        assertNull(db.browserDao().tab(id)!!.faviconUrl)
    }
    @Test fun lateIconUpdatesExistingFavoritesAndBookmarksWithoutChangingTitles() = runBlocking {
        val library = LibraryRepository(workspace)
        val id = tabs.create("personal", "https://example.com")
        library.addBookmark(BrowserTab(id, "personal", "https://example.com", "Custom title"), true)
        library.addBookmark(BrowserTab(id, "personal", "https://example.com/", "Bookmark title"), false)
        library.addBookmark(BrowserTab(id, "personal", "https://other.example", "Other"), false)
        tabs.updatePage(id, "https://example.com/", "Page title", "/saved/icon.png")
        val saved = db.browserDao().observeBookmarks().first()
        assertEquals("/saved/icon.png", saved.first { it.title == "Custom title" }.faviconUrl)
        assertEquals("/saved/icon.png", saved.first { it.title == "Bookmark title" }.faviconUrl)
        assertNull(saved.first { it.title == "Other" }.faviconUrl)
        tabs.updatePage(id, "https://example.com/", "Reloading", null)
        assertEquals("/saved/icon.png", db.browserDao().observeBookmarks().first().first { it.title == "Custom title" }.faviconUrl)
    }
    @Test fun deletedBaiduIsNotReseededOnLaunch() = runBlocking {
        SearchEngineRepository(workspace, settings).delete("baidu")
        workspace.initialize()
        assertTrue(db.browserDao().engines().none { it.id == "baidu" })
    }

}
