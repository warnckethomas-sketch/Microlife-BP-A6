package com.example

import com.example.data.model.BpMeasurement
import com.example.pdf.PdfExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class PdfExportTest {

    @Test
    fun testGroupMeasurementsByDay_PreservesRestingStatusInSlots() {
        val cal = Calendar.getInstance()

        // Slot 1: Morgens (8:15) with isResting = true
        cal.set(2026, Calendar.OCTOBER, 15, 8, 15, 0)
        val m1 = BpMeasurement(
            id = 1,
            timestamp = cal.timeInMillis,
            systole = 120,
            diastole = 80,
            pulse = 68,
            afibDetected = false,
            isResting = true
        )

        // Slot 2: Mittags (12:30) with isResting = false (Unruhe)
        cal.set(2026, Calendar.OCTOBER, 15, 12, 30, 0)
        val m2 = BpMeasurement(
            id = 2,
            timestamp = cal.timeInMillis,
            systole = 145,
            diastole = 92,
            pulse = 84,
            afibDetected = false,
            isResting = false
        )

        // Slot 3: Abends (19:00) with isResting = false (Unruhe)
        cal.set(2026, Calendar.OCTOBER, 15, 19, 0, 0)
        val m3 = BpMeasurement(
            id = 3,
            timestamp = cal.timeInMillis,
            systole = 130,
            diastole = 85,
            pulse = 72,
            afibDetected = true,
            isResting = false
        )

        val dayRows = PdfExporter.groupMeasurementsByDay(listOf(m1, m2, m3))

        assertEquals(1, dayRows.size)
        val day = dayRows[0]

        // Slot 1: Morgens -> m1
        assertNotNull(day.m1)
        assertEquals(120, day.m1!!.systole)
        assertTrue(day.m1!!.isResting)

        // Slot 2: Mittags -> m2
        assertNotNull(day.m2)
        assertEquals(145, day.m2!!.systole)
        assertFalse(day.m2!!.isResting)

        // Slot 3: Abends -> m3
        assertNotNull(day.m3)
        assertEquals(130, day.m3!!.systole)
        assertFalse(day.m3!!.isResting)
    }

    @Test
    fun testFilterCurrentMonthMeasurements_SortsAscending() {
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.OCTOBER, 20, 10, 0, 0)
        val mLater = BpMeasurement(id = 2, timestamp = cal.timeInMillis, systole = 130, diastole = 85, pulse = 70, afibDetected = false, isResting = false)

        cal.set(2026, Calendar.OCTOBER, 5, 8, 0, 0)
        val mEarlier = BpMeasurement(id = 1, timestamp = cal.timeInMillis, systole = 120, diastole = 80, pulse = 65, afibDetected = false, isResting = true)

        val (title, sorted) = PdfExporter.filterCurrentMonthMeasurements(listOf(mLater, mEarlier))
        assertEquals(2, sorted.size)
        assertEquals(1, sorted[0].id) // Earlier first
        assertEquals(2, sorted[1].id) // Later second
        assertTrue(sorted[0].isResting)
        assertFalse(sorted[1].isResting)
    }
}
