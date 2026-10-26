package com.example

import com.example.data.model.BpMeasurement
import com.example.data.repository.BpRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.BpDatabase

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimestampFixTest {

    @Test
    fun testFutureMeasurementsAreSavedAsDistinctEntries() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = BpDatabase.getDatabase(context)
        val repository = BpRepository(db.bpDao(), context)

        val now = System.currentTimeMillis()
        val futureTimeOneHourAhead = now + 3600_000L // 1 Stunde in der Zukunft (vorgestellte Uhr)

        val m1 = BpMeasurement(
            id = 0,
            timestamp = now,
            systole = 120,
            diastole = 80,
            pulse = 70,
            afibDetected = false,
            userIndex = 1
        )

        val m2 = BpMeasurement(
            id = 0,
            timestamp = futureTimeOneHourAhead,
            systole = 120,
            diastole = 80,
            pulse = 70,
            afibDetected = false,
            userIndex = 1
        )

        // Insert first measurement
        val count1 = repository.insertMeasurements(listOf(m1))
        assertEquals(1, count1)

        // Insert second test measurement taken with forward clock
        val count2 = repository.insertMeasurements(listOf(m2))
        assertEquals(1, count2)

        // Verify that BOTH measurements exist as distinct entries
        val allStored = db.bpDao().getAllMeasurementsList()
        assertEquals(2, allStored.size)
    }
}
