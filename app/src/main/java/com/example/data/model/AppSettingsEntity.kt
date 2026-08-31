package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val selectedUserIndex: Int = 1,
    val autoEraseAfterSync: Boolean = true,
    val use12HourTimeFormat: Boolean = false,
    val autoBackupEnabled: Boolean = true,
    val backupDirectoryUri: String = "",
    val backupDirectoryPathDisplay: String = "App-Speicher (Dokumente / Blutdruck_Backups)",
    val lastBackupTimestamp: Long = 0L,
    val chartScaleMin: Int = 30,
    val chartScaleMax: Int = 210
)
