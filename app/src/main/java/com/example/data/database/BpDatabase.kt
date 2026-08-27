package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.BpDao
import com.example.data.model.BpMeasurement

@Database(entities = [BpMeasurement::class], version = 1, exportSchema = false)
abstract class BpDatabase : RoomDatabase() {
    abstract fun bpDao(): BpDao

    companion object {
        @Volatile
        private var INSTANCE: BpDatabase? = null

        fun getDatabase(context: Context): BpDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BpDatabase::class.java,
                    "microlife_bp_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
