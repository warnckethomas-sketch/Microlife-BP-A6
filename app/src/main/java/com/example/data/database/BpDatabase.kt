package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.BpDao
import com.example.data.model.AppSettingsEntity
import com.example.data.model.BpMeasurement
import com.example.data.model.PersonProfileEntity

@Database(
    entities = [
        BpMeasurement::class,
        PersonProfileEntity::class,
        AppSettingsEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class BpDatabase : RoomDatabase() {
    abstract fun bpDao(): BpDao

    companion object {
        @Volatile
        private var INSTANCE: BpDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Bestehende Tabelle 'bp_measurements' bleibt vollständig erhalten
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `person_profiles` (
                        `userIndex` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `systoleNormMax` INTEGER NOT NULL,
                        `diastoleNormMax` INTEGER NOT NULL,
                        `deviceAddress` TEXT NOT NULL,
                        `measurementsPerDay` INTEGER NOT NULL,
                        PRIMARY KEY(`userIndex`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `app_settings` (
                        `id` INTEGER NOT NULL,
                        `selectedUserIndex` INTEGER NOT NULL,
                        `autoEraseAfterSync` INTEGER NOT NULL,
                        `use12HourTimeFormat` INTEGER NOT NULL,
                        `autoBackupEnabled` INTEGER NOT NULL,
                        `backupDirectoryUri` TEXT NOT NULL,
                        `backupDirectoryPathDisplay` TEXT NOT NULL,
                        `lastBackupTimestamp` INTEGER NOT NULL,
                        `chartScaleMin` INTEGER NOT NULL,
                        `chartScaleMax` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): BpDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BpDatabase::class.java,
                    "microlife_bp_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
