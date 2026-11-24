package com.takeruf.nagi

import android.app.Application
import androidx.room.Room
import androidx.room.withTransaction
import com.takeruf.nagi.data.datastore.SettingsStore
import com.takeruf.nagi.data.repository.LibraryRepository
import com.takeruf.nagi.data.repository.TabRepository
import com.takeruf.nagi.data.repository.WorkspaceRepository
import com.takeruf.nagi.data.room.NagiDatabase
import com.takeruf.nagi.data.room.entity
import com.takeruf.nagi.domain.model.BrowserSettings
import com.takeruf.nagi.domain.model.BrowserTab
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
class LibraryHistoryTest {
    private lateinit var db: NagiDatabase
    private lateinit var library: LibraryRepository
    private lateinit var workspace: WorkspaceRepository
    private lateinit var settings: SettingsStore
    private lateinit var tabs: TabRepository
    @Before fun setup() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, NagiDatabase::class.java).build()
        settings = SettingsStore(context)
        settings.update { BrowserSettings() }
        workspace = WorkspaceRepository(db, settings)
        workspace.initialize()
        tabs = TabRepository(workspace, settings)
        library = LibraryRepository(workspace)
        db.browserDao().putTab(BrowserTab("tab", "personal").entity())
        db.browserDao().putTab(BrowserTab("other", "work").entity())
    }
    @After fun close() { db.close() }

    @Test fun repeatedCompletionReloadAndRecreatedSessionKeepOriginalVisit() = runBlocking {
        library.recordVisit("https://example.com/a", "Desktop", "/icon.png", "tab")
        val original = db.browserDao().observeHistory().first().single()
        repeat(10) { library.recordVisit(original.url, "Mobile", null, "tab") }
        val current = db.browserDao().observeHistory().first().single()
        assertEquals(original.id, current.id)
        assertEquals(original.visitedAt, current.visitedAt)
        assertEquals("Mobile", current.title)
        assertEquals("/icon.png", current.faviconUrl)
    }
    @Test fun leavingAndReturningAndOpeningAnotherTabRemainSeparateVisits() = runBlocking {
        library.recordVisit("https://example.com/a", "A", null, "tab")
        library.recordVisit("https://example.com/b", "B", null, "tab")
        library.recordVisit("https://example.com/a", "A again", null, "tab")
        library.recordVisit("https://example.com/a", "Other tab", null, "other")
        val visits = db.browserDao().observeHistory().first()
        assertEquals(4, visits.size)
        assertEquals(3, visits.count { it.url.endsWith("/a") })
    }
    @Test fun deletingOrClearingHistoryAllowsCurrentPageToBeRecordedAgain() = runBlocking {
        library.recordVisit("https://example.com/a", "A", null, "tab")
        library.deleteHistory(db.browserDao().observeHistory().first().single().id)
        library.recordVisit("https://example.com/a", "A", null, "tab")
        assertEquals(1, db.browserDao().observeHistory().first().size)
        library.clearHistory()
        library.recordVisit("https://example.com/a", "A", null, "tab")
        assertEquals(1, db.browserDao().observeHistory().first().size)
        // A record removed by trimming/another DAO operation must not be suppressed either.
        db.browserDao().clearHistory()
        library.recordVisit("https://example.com/a", "A", null, "tab")
        assertEquals(1, db.browserDao().observeHistory().first().size)
    }
    @Test fun concurrentNotificationsForSameTabAreSerialized() = runBlocking {
        coroutineScope { repeat(30) {
            launch(Dispatchers.IO) { library.recordVisit("https://example.com/a", "A", null, "tab") }
        } }
        assertEquals(1, db.browserDao().observeHistory().first().size)
    }
    @Test fun internalPagesAreNotRecordedAndQueriesRemainDistinct() = runBlocking {
        library.recordVisit("about:blank", "New tab", null, "tab")
        library.recordVisit("https://example.com/?q=one", "One", null, "tab")
        library.recordVisit("https://example.com/?q=two", "Two", null, "tab")
        assertEquals(2, db.browserDao().observeHistory().first().size)
    }

    @Test fun backgroundCompletionBelongsToItsTabSpaceAndSameUrlCanExistInEachSpace() = runBlocking {
        settings.update { it.copy(selectedSpaceId = "work") }
        val url = "https://example.com/shared"
        library.recordVisit(url, "Personal visit", null, "tab")
        library.recordVisit(url, "Work visit", null, "other")
        val all = db.browserDao().observeHistory().first()
        assertEquals(setOf("personal", "work"), all.map { it.spaceId }.toSet())
        assertEquals(2, all.map { it.id }.toSet().size)
        assertEquals(listOf("Work visit"), workspace.snapshot.first().history.map { it.title })
        settings.update { it.copy(selectedSpaceId = "personal", spacesEnabled = false) }
        assertEquals(listOf("Personal visit"), workspace.snapshot.first().history.map { it.title })
    }

    @Test fun movingTabStartsNewSpaceVisitWithoutMovingOrEditingItsPreviousHistory() = runBlocking {
        val url = "https://example.com/moved"
        library.recordVisit(url, "Before move", "/personal.png", "tab")
        val old = db.browserDao().observeHistory().first().single()
        tabs.moveToSpace("tab", "work")
        library.recordVisit(url, "After move", "/work.png", "tab")
        assertEquals(old, db.browserDao().observeHistoryForSpace("personal").first().single())
        assertEquals("After move", db.browserDao().observeHistoryForSpace("work").first().single().title)
    }

    @Test fun scopedRemovalAndClearDoNotTouchOtherSpacesAndGlobalPrivacyClearStillDoes() = runBlocking {
        library.recordVisit("https://example.com/a", "Personal", null, "tab")
        library.recordVisit("https://example.com/a", "Work", null, "other")
        val personal = db.browserDao().observeHistoryForSpace("personal").first().single()
        library.deleteHistory(personal.id, "work")
        assertEquals(personal, db.browserDao().observeHistoryForSpace("personal").first().single())
        library.clearHistoryForSpace("work")
        assertTrue(db.browserDao().observeHistoryForSpace("work").first().isEmpty())
        assertEquals(personal, db.browserDao().observeHistoryForSpace("personal").first().single())
        library.recordVisit("https://example.com/a", "Work again", null, "other")
        assertEquals(2, db.browserDao().observeHistory().first().size)
        library.clearHistory()
        assertTrue(db.browserDao().observeHistory().first().isEmpty())
    }

    @Test fun deletedSpaceCascadesHistoryAndLateCallbacksDoNotRecreateIt() = runBlocking {
        library.recordVisit("https://example.com/a", "Personal", null, "tab")
        library.recordVisit("https://example.com/a", "Work", null, "other")
        db.browserDao().deleteSpace("work")
        library.recordVisit("https://example.com/a", "Late Work", null, "other")
        val remaining = db.browserDao().observeHistory().first()
        assertEquals(1, remaining.size)
        assertEquals("personal", remaining.single().spaceId)
        tabs.close("tab")
        library.recordVisit("https://example.com/late", "Late closed tab", null, "tab")
        assertEquals(remaining, db.browserDao().observeHistory().first())
    }

    @Test fun retentionAndVisibleLimitsAreIndependentForEachSpace() = runBlocking {
        db.withTransaction {
            for (space in listOf("personal", "work")) db.openHelper.writableDatabase.execSQL("""
                WITH RECURSIVE visits(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM visits WHERE n < 5000)
                INSERT INTO history(url, title, faviconUrl, visitedAt, spaceId)
                SELECT 'https://seed.test/' || n, 'Saved ' || n, NULL, n, ? FROM visits
            """.trimIndent(), arrayOf(space))
        }
        val work = db.browserDao().observeHistoryForSpace("work").first()
        assertEquals(2000, work.size)
        library.recordVisit("https://example.com/new", "Newest personal", null, "tab")
        val all = db.browserDao().observeHistory().first()
        assertEquals(5000, all.count { it.spaceId == "personal" })
        assertEquals(5000, all.count { it.spaceId == "work" })
        assertFalse(all.any { it.spaceId == "personal" && it.visitedAt == 1L })
        assertTrue(all.any { it.spaceId == "work" && it.visitedAt == 1L })
        assertEquals(work, db.browserDao().observeHistoryForSpace("work").first())
        assertEquals("Newest personal", db.browserDao().observeHistoryForSpace("personal").first().first().title)
    }
}
