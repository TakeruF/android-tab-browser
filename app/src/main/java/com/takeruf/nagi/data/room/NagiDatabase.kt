package com.takeruf.nagi.data.room

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SpaceEntity::class, TabEntity::class, SearchEngineEntity::class,
    HistoryEntity::class, BookmarkEntity::class], version = 3, exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)])
abstract class NagiDatabase : RoomDatabase() { abstract fun browserDao(): BrowserDao }
