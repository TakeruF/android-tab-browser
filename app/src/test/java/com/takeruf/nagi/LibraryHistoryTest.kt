package com.takeruf.nagi

import android.app.Application
import androidx.room.Room
import com.takeruf.nagi.data.datastore.SettingsStore
import com.takeruf.nagi.data.repository.LibraryRepository
import com.takeruf.nagi.data.repository.WorkspaceRepository
import com.takeruf.nagi.data.room.NagiDatabase
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
    @Before fun setup() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, NagiDatabase::class.java).build()
        val workspace = WorkspaceRepository(db, SettingsStore(context))
        workspace.initialize()
        library = LibraryRepository(workspace)
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
}
