package com.willyshare.willykez.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TransferEntity::class, FileItemEntity::class], version = 3, exportSchema = false)
abstract class PulseDatabase : RoomDatabase() {
    abstract fun pulseDao(): PulseDao

    companion object {
        @Volatile
        private var INSTANCE: PulseDatabase? = null

        fun getDatabase(context: Context): PulseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PulseDatabase::class.java,
                    "pulse_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
