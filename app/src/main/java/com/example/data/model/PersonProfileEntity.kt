package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.repository.PersonProfile

@Entity(tableName = "person_profiles")
data class PersonProfileEntity(
    @PrimaryKey
    val userIndex: Int = 1,
    val name: String = "Person 1",
    val systoleNormMax: Int = 135,
    val diastoleNormMax: Int = 85,
    val deviceAddress: String = "",
    val measurementsPerDay: Int = 2,
    val birthDate: String = ""
) {
    fun toDomain(): PersonProfile {
        return PersonProfile(
            userIndex = userIndex,
            name = name,
            systoleNormMax = systoleNormMax,
            diastoleNormMax = diastoleNormMax,
            deviceAddress = deviceAddress,
            measurementsPerDay = measurementsPerDay,
            birthDate = birthDate
        )
    }

    companion object {
        fun fromDomain(profile: PersonProfile): PersonProfileEntity {
            return PersonProfileEntity(
                userIndex = profile.userIndex,
                name = profile.name,
                systoleNormMax = profile.systoleNormMax,
                diastoleNormMax = profile.diastoleNormMax,
                deviceAddress = profile.deviceAddress,
                measurementsPerDay = profile.measurementsPerDay,
                birthDate = profile.birthDate
            )
        }
    }
}
