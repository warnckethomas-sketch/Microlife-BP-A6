package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ble.MicrolifeBleManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MicrolifeProtocolTest {

    private fun getManager(): MicrolifeBleManager {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return MicrolifeBleManager(context)
    }

    @Test
    fun testEncodeHourByte_24HourMode() {
        val manager = getManager()
        
        // 24h mode: 00:00 -> 0x00, 09:35 -> 0x09, 21:35 -> 0x15 (21 dec)
        assertEquals(0x00.toByte(), manager.encodeHourByte(0, is12HourMode = false))
        assertEquals(0x09.toByte(), manager.encodeHourByte(9, is12HourMode = false))
        assertEquals(0x0C.toByte(), manager.encodeHourByte(12, is12HourMode = false))
        assertEquals(0x15.toByte(), manager.encodeHourByte(21, is12HourMode = false))
        assertEquals(0x17.toByte(), manager.encodeHourByte(23, is12HourMode = false))
    }

    @Test
    fun testEncodeHourByte_12HourAmPmMode() {
        val manager = getManager()

        // AM cases (00:00 - 11:59): Bit 7 (0x80) NOT set
        // 00:00 (12 AM) -> 12 (0x0C)
        assertEquals(0x0C.toByte(), manager.encodeHourByte(0, is12HourMode = true))
        // 09:35 (9 AM) -> 9 (0x09)
        assertEquals(0x09.toByte(), manager.encodeHourByte(9, is12HourMode = true))
        // 11:00 (11 AM) -> 11 (0x0B)
        assertEquals(0x0B.toByte(), manager.encodeHourByte(11, is12HourMode = true))

        // PM cases (12:00 - 23:59): Bit 7 (0x80) SET
        // 12:00 (12 PM) -> 12 | 0x80 = 0x8C
        assertEquals(0x8C.toByte(), manager.encodeHourByte(12, is12HourMode = true))
        // 13:00 (1 PM) -> 1 | 0x80 = 0x81
        assertEquals(0x81.toByte(), manager.encodeHourByte(13, is12HourMode = true))
        // 21:35 (9 PM) -> 9 | 0x80 = 0x89 (Matches exact user specification)
        assertEquals(0x89.toByte(), manager.encodeHourByte(21, is12HourMode = true))
        // 23:59 (11 PM) -> 11 | 0x80 = 0x8B
        assertEquals(0x8B.toByte(), manager.encodeHourByte(23, is12HourMode = true))
    }

    @Test
    fun testBuildTimeCommand9Byte_ChecksumCalculation() {
        val manager = getManager()
        val cmd24 = manager.buildTimeCommand9Byte(headerByte = 0xFF.toByte(), opcode = 0xFE.toByte(), is12HourMode = false)
        assertEquals(13, cmd24.size)
        assertEquals(0x4D.toByte(), cmd24[0])
        assertEquals(0xFF.toByte(), cmd24[1])
        assertEquals(0x00.toByte(), cmd24[2])
        assertEquals(0x09.toByte(), cmd24[3])
        assertEquals(0xFE.toByte(), cmd24[11])

        var sum24 = 0
        for (i in 0 until cmd24.size - 1) {
            sum24 += (cmd24[i].toInt() and 0xFF)
        }
        assertEquals((sum24 and 0xFF).toByte(), cmd24[cmd24.size - 1])

        val cmd12 = manager.buildTimeCommand9Byte(headerByte = 0xFF.toByte(), opcode = 0xFE.toByte(), is12HourMode = true)
        assertEquals(13, cmd12.size)
        var sum12 = 0
        for (i in 0 until cmd12.size - 1) {
            sum12 += (cmd12[i].toInt() and 0xFF)
        }
        assertEquals((sum12 and 0xFF).toByte(), cmd12[cmd12.size - 1])
    }
}
