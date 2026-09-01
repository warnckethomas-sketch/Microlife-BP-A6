package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.dao.BpDao
import com.example.data.model.AppSettingsEntity
import com.example.data.model.BpMeasurement
import com.example.data.model.PersonProfileEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PersonProfile(
    val userIndex: Int = 1,
    val name: String = "Person 1",
    val systoleNormMax: Int = 135,
    val diastoleNormMax: Int = 85,
    val deviceAddress: String = "", // Paired BLE Device MAC / Name
    val measurementsPerDay: Int = 2, // Geplante Messungen pro Tag (z.B. 2x, 3x, 4x)
    val birthDate: String = "" // Geburtsdatum z.B. "15.04.1958"
)

data class UserSettings(
    val selectedUserIndex: Int = 1,
    val person1: PersonProfile = PersonProfile(1, "Person 1 (Maria)", 135, 85, "", 2, ""),
    val person2: PersonProfile = PersonProfile(2, "Person 2 (Thomas)", 140, 90, "", 2, ""),
    val autoEraseAfterSync: Boolean = true,
    val use12HourTimeFormat: Boolean = false,
    val autoBackupEnabled: Boolean = true,
    val backupDirectoryUri: String = "",
    val backupDirectoryPathDisplay: String = "App-Speicher (Dokumente / Blutdruck_Backups)",
    val lastBackupTimestamp: Long = 0L,
    val chartScaleMin: Int = 30,
    val chartScaleMax: Int = 210
) {
    val activePerson: PersonProfile
        get() = if (selectedUserIndex == 2) person2 else person1

    val patientName: String
        get() = activePerson.name

    val systoleNormMax: Int
        get() = activePerson.systoleNormMax

    val diastoleNormMax: Int
        get() = activePerson.diastoleNormMax

    val measurementsPerDay: Int
        get() = activePerson.measurementsPerDay.coerceAtLeast(1)

    val defaultFileName: String
        get() {
            val sanitized = activePerson.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            return "Blutdruck_Protokoll_${sanitized}.pdf"
        }
}

class BpRepository(
    private val bpDao: BpDao,
    context: Context
) {
    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences =
        context.getSharedPreferences("microlife_settings", Context.MODE_PRIVATE)

    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    init {
        // Asynchron aus Room-Datenbank laden bzw. initiale Profile in DB absichern
        repositoryScope.launch {
            try {
                // Bereinige fehlerhafte Zukunftsmessungen aus vorherigen Fehlern
                purgeFutureMeasurements()

                val dbProfiles = bpDao.getAllPersonProfiles()
                val dbSettings = bpDao.getAppSettings()

                if (dbProfiles.isNotEmpty()) {
                    val p1 = dbProfiles.find { it.userIndex == 1 }?.toDomain() ?: _settings.value.person1
                    val p2 = dbProfiles.find { it.userIndex == 2 }?.toDomain() ?: _settings.value.person2

                    val updated = _settings.value.copy(
                        person1 = p1,
                        person2 = p2,
                        selectedUserIndex = dbSettings?.selectedUserIndex ?: _settings.value.selectedUserIndex,
                        autoEraseAfterSync = dbSettings?.autoEraseAfterSync ?: _settings.value.autoEraseAfterSync,
                        use12HourTimeFormat = dbSettings?.use12HourTimeFormat ?: _settings.value.use12HourTimeFormat,
                        autoBackupEnabled = dbSettings?.autoBackupEnabled ?: _settings.value.autoBackupEnabled,
                        backupDirectoryUri = dbSettings?.backupDirectoryUri ?: _settings.value.backupDirectoryUri,
                        backupDirectoryPathDisplay = dbSettings?.backupDirectoryPathDisplay ?: _settings.value.backupDirectoryPathDisplay,
                        lastBackupTimestamp = dbSettings?.lastBackupTimestamp ?: _settings.value.lastBackupTimestamp,
                        chartScaleMin = dbSettings?.chartScaleMin ?: _settings.value.chartScaleMin,
                        chartScaleMax = dbSettings?.chartScaleMax ?: _settings.value.chartScaleMax
                    )
                    _settings.value = updated
                    saveToPreferences(updated)
                } else {
                    // Initialzustand in die Room-Datenbank schreiben
                    persistSettingsToDatabase(_settings.value)
                }
            } catch (e: Exception) {
                // Bei Migration oder Erststart Fallback auf Prefs
            }
        }
    }

    val allMeasurements: Flow<List<BpMeasurement>> = bpDao.getAllMeasurements()

    suspend fun getAllMeasurementsList(): List<BpMeasurement> {
        return bpDao.getAllMeasurementsList()
    }

    fun getMeasurementsForUser(userIndex: Int): Flow<List<BpMeasurement>> {
        return bpDao.getMeasurementsForUser(userIndex)
    }

    private fun loadSettings(): UserSettings {
        val selectedUser = prefs.getInt("selected_user_index", 1)

        val p1Name = prefs.getString("p1_name", "Person 1 (Maria)") ?: "Person 1 (Maria)"
        val p1Sys = prefs.getInt("p1_sys_norm", 135)
        val p1Dia = prefs.getInt("p1_dia_norm", 85)
        val p1Device = prefs.getString("p1_device", "") ?: ""
        val p1DailyTarget = prefs.getInt("p1_measurements_per_day", 2)
        val p1BirthDate = prefs.getString("p1_birth_date", "") ?: ""

        val p2Name = prefs.getString("p2_name", "Person 2 (Thomas)") ?: "Person 2 (Thomas)"
        val p2Sys = prefs.getInt("p2_sys_norm", 140)
        val p2Dia = prefs.getInt("p2_dia_norm", 90)
        val p2Device = prefs.getString("p2_device", "") ?: ""
        val p2DailyTarget = prefs.getInt("p2_measurements_per_day", 2)
        val p2BirthDate = prefs.getString("p2_birth_date", "") ?: ""

        val autoErase = prefs.getBoolean("auto_erase", true)
        val use12Hour = prefs.getBoolean("use_12_hour_format", false)
        val autoBackup = prefs.getBoolean("auto_backup_enabled", true)
        val backupUri = prefs.getString("backup_dir_uri", "") ?: ""
        val rawBackupDisplay = prefs.getString("backup_dir_display", "App-Speicher (Dokumente / Blutdruck_Backups)")
            ?: "App-Speicher (Dokumente / Blutdruck_Backups)"
        val backupDisplay = com.example.data.backup.DatabaseBackupManager.sanitizeDisplayName(appContext, backupUri, rawBackupDisplay)
        val lastBackup = prefs.getLong("last_backup_timestamp", 0L)
        val chartScaleMin = prefs.getInt("chart_scale_min", 30)
        val chartScaleMax = prefs.getInt("chart_scale_max", 210)

        return UserSettings(
            selectedUserIndex = selectedUser,
            person1 = PersonProfile(1, p1Name, p1Sys, p1Dia, p1Device, p1DailyTarget, p1BirthDate),
            person2 = PersonProfile(2, p2Name, p2Sys, p2Dia, p2Device, p2DailyTarget, p2BirthDate),
            autoEraseAfterSync = autoErase,
            use12HourTimeFormat = use12Hour,
            autoBackupEnabled = autoBackup,
            backupDirectoryUri = backupUri,
            backupDirectoryPathDisplay = backupDisplay,
            lastBackupTimestamp = lastBackup,
            chartScaleMin = chartScaleMin,
            chartScaleMax = chartScaleMax
        )
    }

    private fun saveToPreferences(newSettings: UserSettings) {
        prefs.edit()
            .putInt("selected_user_index", newSettings.selectedUserIndex)
            .putString("p1_name", newSettings.person1.name)
            .putInt("p1_sys_norm", newSettings.person1.systoleNormMax)
            .putInt("p1_dia_norm", newSettings.person1.diastoleNormMax)
            .putString("p1_device", newSettings.person1.deviceAddress)
            .putInt("p1_measurements_per_day", newSettings.person1.measurementsPerDay)
            .putString("p1_birth_date", newSettings.person1.birthDate)
            .putString("p2_name", newSettings.person2.name)
            .putInt("p2_sys_norm", newSettings.person2.systoleNormMax)
            .putInt("p2_dia_norm", newSettings.person2.diastoleNormMax)
            .putString("p2_device", newSettings.person2.deviceAddress)
            .putInt("p2_measurements_per_day", newSettings.person2.measurementsPerDay)
            .putString("p2_birth_date", newSettings.person2.birthDate)
            .putBoolean("auto_erase", newSettings.autoEraseAfterSync)
            .putBoolean("use_12_hour_format", newSettings.use12HourTimeFormat)
            .putBoolean("auto_backup_enabled", newSettings.autoBackupEnabled)
            .putString("backup_dir_uri", newSettings.backupDirectoryUri)
            .putString("backup_dir_display", newSettings.backupDirectoryPathDisplay)
            .putLong("last_backup_timestamp", newSettings.lastBackupTimestamp)
            .putInt("chart_scale_min", newSettings.chartScaleMin)
            .putInt("chart_scale_max", newSettings.chartScaleMax)
            .apply()
    }

    private suspend fun persistSettingsToDatabase(newSettings: UserSettings) {
        try {
            val p1Entity = PersonProfileEntity.fromDomain(newSettings.person1)
            val p2Entity = PersonProfileEntity.fromDomain(newSettings.person2)
            bpDao.insertPersonProfiles(listOf(p1Entity, p2Entity))

            val settingsEntity = AppSettingsEntity(
                id = 1,
                selectedUserIndex = newSettings.selectedUserIndex,
                autoEraseAfterSync = newSettings.autoEraseAfterSync,
                use12HourTimeFormat = newSettings.use12HourTimeFormat,
                autoBackupEnabled = newSettings.autoBackupEnabled,
                backupDirectoryUri = newSettings.backupDirectoryUri,
                backupDirectoryPathDisplay = newSettings.backupDirectoryPathDisplay,
                lastBackupTimestamp = newSettings.lastBackupTimestamp,
                chartScaleMin = newSettings.chartScaleMin,
                chartScaleMax = newSettings.chartScaleMax
            )
            bpDao.insertAppSettings(settingsEntity)
        } catch (e: Exception) {
            // Log or ignore
        }
    }

    fun saveSettings(newSettings: UserSettings) {
        saveToPreferences(newSettings)
        _settings.value = newSettings
        repositoryScope.launch {
            persistSettingsToDatabase(newSettings)
        }
    }

    suspend fun saveSettingsSync(newSettings: UserSettings) {
        saveToPreferences(newSettings)
        _settings.value = newSettings
        persistSettingsToDatabase(newSettings)
    }

    fun updateLastBackupTimestamp(timestamp: Long) {
        prefs.edit().putLong("last_backup_timestamp", timestamp).apply()
        _settings.value = _settings.value.copy(lastBackupTimestamp = timestamp)
    }

    suspend fun purgeFutureMeasurements(): Int {
        val futureLimit = System.currentTimeMillis() + 10 * 60 * 1000L // 10 Min Toleranz
        return bpDao.deleteFutureMeasurements(futureLimit)
    }

    suspend fun insertMeasurement(measurement: BpMeasurement): Long {
        // Bereinige etwaige fehlerhafte Zukunftsmessungen
        purgeFutureMeasurements()
        val existing = bpDao.getAllMeasurementsList()
        val isDuplicate = existing.any { oldM ->
            oldM.userIndex == measurement.userIndex &&
            (
                (Math.abs(oldM.timestamp - measurement.timestamp) < 60_000L && oldM.systole == measurement.systole && oldM.diastole == measurement.diastole) ||
                (oldM.timestamp == measurement.timestamp && oldM.systole == measurement.systole)
            )
        }
        if (isDuplicate) {
            return -1L
        }
        return bpDao.insertMeasurement(measurement)
    }

    suspend fun insertMeasurements(measurements: List<BpMeasurement>): Int {
        // Bereinige etwaige fehlerhafte Zukunftsmessungen
        purgeFutureMeasurements()
        val existing = bpDao.getAllMeasurementsList()
        val toInsert = measurements.filter { newM ->
            val isDuplicate = existing.any { oldM ->
                oldM.userIndex == newM.userIndex &&
                (
                    (Math.abs(oldM.timestamp - newM.timestamp) < 60_000L && oldM.systole == newM.systole && oldM.diastole == newM.diastole) ||
                    (oldM.timestamp == newM.timestamp && oldM.systole == newM.systole)
                )
            }
            !isDuplicate
        }
        if (toInsert.isNotEmpty()) {
            bpDao.insertMeasurements(toInsert)
        }
        return toInsert.size
    }

    suspend fun deleteMeasurement(id: Int) {
        bpDao.deleteMeasurementById(id)
    }

    suspend fun deleteAll() {
        bpDao.deleteAllMeasurements()
    }
}
