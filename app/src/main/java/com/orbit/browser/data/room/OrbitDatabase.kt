package com.orbit.browser.data.room

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SpaceEntity::class, TabEntity::class, SearchEngineEntity::class,
    HistoryEntity::class, BookmarkEntity::class], version = 1, exportSchema = true)
abstract class OrbitDatabase : RoomDatabase() { abstract fun browserDao(): BrowserDao }
