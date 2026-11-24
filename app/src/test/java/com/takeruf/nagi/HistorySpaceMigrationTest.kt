package com.takeruf.nagi

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.takeruf.nagi.data.datastore.SettingsStore
import com.takeruf.nagi.data.repository.WorkspaceRepository
import com.takeruf.nagi.data.room.NagiDatabase
import com.takeruf.nagi.domain.model.BrowserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** Real exported v3 schema -> current Room schema, including old ids and FK ownership. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HistorySpaceMigrationTest {
    private fun migrate(spaces: List<Pair<String, Int>>, verify: suspend (NagiDatabase) -> Unit) = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "history-space-migration-${System.nanoTime()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        val schema = JSONObject(File("schemas/com.takeruf.nagi.data.room.NagiDatabase/3.json").readText())
            .getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: JSONArray()
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j)
                    .getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            spaces.forEach { (id, position) ->
                old.execSQL("INSERT INTO spaces VALUES (?, ?, 'lucide:user-round', 0, ?, NULL)",
                    arrayOf<Any>(id, id, position))
            }
            old.execSQL("INSERT INTO history VALUES (17, 'https://legacy.test/a', 'Earlier A', '/a.png', 100)")
            old.execSQL("INSERT INTO history VALUES (42, 'https://legacy.test/b', 'Earlier B', NULL, 200)")
            old.version = 3
        }
        val migrated = Room.databaseBuilder(context, NagiDatabase::class.java, name).build()
        try {
            val history = migrated.browserDao().observeHistory().first()
            assertEquals(listOf(42L, 17L), history.map { it.id })
            assertEquals(listOf("Earlier B", "Earlier A"), history.map { it.title })
            assertEquals(listOf(200L, 100L), history.map { it.visitedAt })
            assertEquals("/a.png", history.last().faviconUrl)
            verify(migrated)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun prefersExistingPersonalAndPreservesVisitsUntilThatSpaceIsDeleted() =
        migrate(listOf("work" to 0, "personal" to 1)) { db ->
            assertEquals(setOf("personal"), db.browserDao().observeHistory().first().map { it.spaceId }.toSet())
            assertTrue(db.browserDao().observeHistoryForSpace("work").first().isEmpty())
            db.browserDao().deleteSpace("personal")
            assertTrue(db.browserDao().observeHistory().first().isEmpty())
        }

    @Test fun missingPersonalUsesTheFirstExistingSpaceWithoutDuplicatingOldVisits() =
        migrate(listOf("study" to 2, "work" to 0)) { db ->
            assertEquals(setOf("work"), db.browserDao().observeHistory().first().map { it.spaceId }.toSet())
            assertTrue(db.browserDao().observeHistoryForSpace("study").first().isEmpty())
        }

    @Test fun absentSpacesKeepVisitsUntilInitializationCanAssignTheSeededSpace() = migrate(emptyList()) { db ->
        assertTrue(db.browserDao().observeHistory().first().all { it.spaceId == null })
        val settings = SettingsStore(RuntimeEnvironment.getApplication())
        settings.update { BrowserSettings() }
        val workspace = WorkspaceRepository(db, settings)
        workspace.initialize()
        assertEquals(setOf("personal"), db.browserDao().observeHistory().first().map { it.spaceId }.toSet())
        assertEquals(2, workspace.snapshot.first().history.size)
    }
}
