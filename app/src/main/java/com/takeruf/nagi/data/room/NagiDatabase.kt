package com.takeruf.nagi.data.room

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SpaceEntity::class, TabEntity::class, SearchEngineEntity::class,
    HistoryEntity::class, BookmarkEntity::class], version = 4, exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4, spec = HistorySpaceMigration::class)])
abstract class NagiDatabase : RoomDatabase() { abstract fun browserDao(): BrowserDao }

/** Earlier versions did not record ownership; retain their visits in one existing Space. */
class HistorySpaceMigration : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL("""UPDATE history SET spaceId = (SELECT id FROM spaces
            ORDER BY CASE WHEN id = 'personal' THEN 0 ELSE 1 END, position, id LIMIT 1)
            WHERE spaceId IS NULL""")
    }
}
