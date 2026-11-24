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
class FavoriteEditingTest {
    private lateinit var db: NagiDatabase
    private lateinit var library: LibraryRepository
    private lateinit var tabs: TabRepository
    private val original = Bookmark("edit-a", "https://example.com/a", "Original", "/icon.png", isFavorite = true, createdAt = 100)
    private val other = Bookmark("edit-b", "https://example.com/b", "Other", isFavorite = true, createdAt = 200)
    private val ordinary = Bookmark("edit-ordinary", "https://example.com/bookmark", "Bookmark", createdAt = 300)

    @Before fun setup() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, NagiDatabase::class.java).build()
        val settings = SettingsStore(context)
        val workspace = WorkspaceRepository(db, settings)
        workspace.initialize()
        library = LibraryRepository(workspace)
        tabs = TabRepository(workspace, settings)
        listOf(original, other, ordinary).forEach { db.browserDao().putBookmark(it.entity()) }
    }
    @After fun close() { db.close() }
    private suspend fun rows() = db.browserDao().observeBookmarks().first()

    @Test fun titleEditPreservesIdentityOrderingIconAndOtherEntries() = runBlocking {
        val before = rows()
        library.editFavorite(original.id, "  日本語の名前  ", original.url)
        assertEquals(before.map { if (it.id == original.id) it.copy(title = "日本語の名前") else it }, rows())
    }

    @Test fun changedUrlIsNormalizedAndItsOldIconIsDiscarded() = runBlocking {
        val before = rows()
        library.editFavorite(original.id, "Edited", " EXAMPLE.org/new?q=1#part ")
        assertEquals(before.map {
            if (it.id == original.id) it.copy(title = "Edited", url = "https://example.org/new?q=1#part", faviconUrl = null) else it
        }, rows())
    }

    @Test fun invalidNamesAndAddressesLeaveAllRowsUntouched() = runBlocking {
        val before = rows()
        for ((name, url) in listOf(" " to original.url, "Name" to "not a url", "Name" to "javascript:alert(1)",
            "Name" to "https://user:password@example.com")) {
            try {
                library.editFavorite(original.id, name, url)
                fail("Invalid edit should be rejected")
            } catch (_: IllegalArgumentException) { }
            assertEquals(before, rows())
        }
    }

    @Test fun editingCannotCreateADuplicateFavoriteUrl() = runBlocking {
        val before = rows()
        try {
            library.editFavorite(original.id, "Duplicate", "HTTPS://EXAMPLE.COM/b")
            fail("Duplicate favorite URL should be rejected")
        } catch (_: IllegalArgumentException) { }
        assertEquals(before, rows())
        library.editFavorite(original.id, original.title, original.url)
        assertEquals(before, rows())
    }

    @Test fun staleEditorCannotResurrectDeletedFavoritesOrChangeOrdinaryBookmarks() = runBlocking {
        library.removeBookmark(original.id)
        val before = rows()
        library.editFavorite(original.id, "Resurrected", "https://example.org/new")
        library.editFavorite(ordinary.id, "Changed", "https://example.org/ordinary")
        assertEquals(before, rows())
    }

    @Test fun concurrentReorderingAndEditingRetainBothChanges() = runBlocking {
        coroutineScope {
            launch(Dispatchers.IO) { tabs.reorderFavorite(original.id, other.id, after = true) }
            launch(Dispatchers.IO) { library.editFavorite(original.id, "Edited", "https://example.org/new") }
        }
        val rows = rows()
        assertTrue(rows.indexOfFirst { it.id == other.id } < rows.indexOfFirst { it.id == original.id })
        assertEquals("Edited", rows.first { it.id == original.id }.title)
        assertEquals("https://example.org/new", rows.first { it.id == original.id }.url)
        assertEquals(ordinary.entity(), rows.first { it.id == ordinary.id })
    }
}
