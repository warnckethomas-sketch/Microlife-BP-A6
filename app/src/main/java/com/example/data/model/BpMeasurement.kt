package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(tableName = "bp_measurements")
data class BpMeasurement(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val systole: Int,
    val diastole: Int,
    val pulse: Int,
    val afibDetected: Boolean,
    val userIndex: Int = 1,
    val notes: String = ""
) {
    fun formattedDateTime(): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
        return sdf.format(Date(timestamp))
    }

    fun formattedDate(): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
        return sdf.format(Date(timestamp))
    }

    fun formattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.GERMANY)
        return sdf.format(Date(timestamp))
    }

    fun isWithinNorm(sysNorm: Int = 135, diaNorm: Int = 85): Boolean {
        return systole < sysNorm && diastole < diaNorm
    }

    fun categoryText(sysNorm: Int = 135, diaNorm: Int = 85): String {
        return if (isWithinNorm(sysNorm, diaNorm)) {
            "Im Normbereich"
        } else if (systole >= 140 || diastole >= 90) {
            "Erhöht (Hypertonie)"
        } else {
            "Leicht erhöht"
        }
    }
}
