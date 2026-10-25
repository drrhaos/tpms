package com.tpms.app.domain

import com.tpms.app.domain.model.TireSensor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Slot placement itself is covered by [WheelAssignmentTest]. */
class WheelLayoutTest {

    private fun sensor(id: String, label: String = id) = TireSensor(
        id = id,
        label = label,
        pressureKpa = 240f,
        temperatureCelsius = 20f,
        batteryPercent = 80,
        alertType = null,
        timestamp = 1L
    )

    @Test
    fun allSlots_includesSpareWhenEnabled() {
        assertEquals(5, WheelLayout.allSlots(showSpareWheel = true).size)
        assertEquals(WheelLayout.SPARE_SLOT, WheelLayout.allSlots(true).last())
    }

    @Test
    fun allSlots_withoutSpareIsTheFourWheels() {
        assertEquals(listOf("FL", "FR", "RL", "RR"), WheelLayout.allSlots(showSpareWheel = false))
    }

    @Test
    fun isSpare_byIdOrLabel() {
        assertTrue(WheelLayout.isSpare(sensor("SP")))
        assertTrue(WheelLayout.isSpare(sensor("ABC123", label = "SP")))
        assertFalse(WheelLayout.isSpare(sensor("FL")))
    }
}
