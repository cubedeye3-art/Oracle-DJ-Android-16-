package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TrackEntity::class, ProjectEntity::class],
    version = 1,
    exportSchema = false
)
abstract class DjDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun projectDao(): ProjectDao

    companion object {
        @Volatile
        private var INSTANCE: DjDatabase? = null

        fun getInstance(context: Context): DjDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DjDatabase::class.java,
                    "pocodj_database.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
