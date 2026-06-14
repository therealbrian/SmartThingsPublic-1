package com.plexbooks.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ProgressEntity::class, DownloadEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
    abstract fun downloadDao(): DownloadDao
}
