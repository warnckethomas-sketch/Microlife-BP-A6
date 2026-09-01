package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.AppSettingsEntity
import com.example.data.model.BpMeasurement
import com.example.data.model.PersonProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BpDao {
    @Query("SELECT * FROM bp_measurements ORDER BY timestamp DESC")
    fun getAllMeasurements(): Flow<List<BpMeasurement>>

    @Query("SELECT * FROM bp_measurements ORDER BY timestamp DESC")
    suspend fun getAllMeasurementsList(): List<BpMeasurement>

    @Query("SELECT * FROM bp_measurements WHERE userIndex = :userIndex ORDER BY timestamp DESC")
    fun getMeasurementsForUser(userIndex: Int): Flow<List<BpMeasurement>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeasurement(measurement: BpMeasurement): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeasurements(measurements: List<BpMeasurement>)

    @Query("DELETE FROM bp_measurements WHERE id = :id")
    suspend fun deleteMeasurementById(id: Int)

    @Query("DELETE FROM bp_measurements WHERE timestamp > :futureLimit")
    suspend fun deleteFutureMeasurements(futureLimit: Long): Int

    @Query("DELETE FROM bp_measurements")
    suspend fun deleteAllMeasurements()

    // Person Profiles
    @Query("SELECT * FROM person_profiles ORDER BY userIndex ASC")
    suspend fun getAllPersonProfiles(): List<PersonProfileEntity>

    @Query("SELECT * FROM person_profiles WHERE userIndex = :userIndex LIMIT 1")
    suspend fun getPersonProfile(userIndex: Int): PersonProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersonProfiles(profiles: List<PersonProfileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersonProfile(profile: PersonProfileEntity)

    // App Settings
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun getAppSettings(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppSettings(settings: AppSettingsEntity)
}
