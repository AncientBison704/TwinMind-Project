package com.example.twinmindproject.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ChunkEntity::class, SummaryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun chunkDao(): ChunkDao
    abstract fun summaryDao(): SummaryDao

    companion object {
        @Volatile private var INSTANCE: AppDb? = null

        fun get(context: Context): AppDb {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, AppDb::class.java, "app.db")
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
