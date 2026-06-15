package com.tpms.app.domain

import com.tpms.app.domain.model.TireSensor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
    fun orderedSlots_usesWheelLabelsFirst() {
        val sensors = mapOf(
            "FL" to sensor("FL"),
            "FR" to sensor("FR"),
            "RL" to sensor("RL"),
            "RR" to sensor("RR")
        )

        val slots = WheelLayout.orderedSlots(sensors)

        assertEquals(listOf("FL", "FR", "RL", "RR"), slots.map { it?.id })
    }

    @Test
    fun orderedSlots_fallsBackToSensorIds() {
        val sensors = mapOf(
            "SENSOR_01" to sensor("SENSOR_01", "FL"),
            "SENSOR_02" to sensor("SENSOR_02", "FR")
        )

        val slots = WheelLayout.orderedSlots(sensors)

        assertEquals("SENSOR_01", slots[0]?.id)
        assertEquals("SENSOR_02", slots[1]?.id)
        assertNull(slots[2])
        assertNull(slots[3])
    }

    @Test
    fun orderedSlots_usesCustomMapping() {
        val sensors = mapOf(
            "ABC123" to sensor("ABC123"),
            "XYZ789" to sensor("XYZ789")
        )
        val mapping = mapOf(
            "FL" to "ABC123",
            "FR" to "XYZ789"
        )

        val slots = WheelLayout.orderedSlots(sensors, mapping)

        assertEquals("ABC123", slots[0]?.id)
        assertEquals("XYZ789", slots[1]?.id)
        assertNull(slots[2])
        assertNull(slots[3])
    }

    @Test
    fun resolveWheelLabel_prefersMapping() {
        val sensor = sensor("ABC123", "ignored")
        val label = WheelLayout.resolveWheelLabel(sensor, mapOf("FL" to "ABC123"))
        assertEquals("FL", label)
    }

    @Test
    fun allSlots_includesSpareWhenEnabled() {
        assertEquals(5, WheelLayout.allSlots(showSpareWheel = true).size)
        assertEquals(WheelLayout.SPARE_SLOT, WheelLayout.allSlots(true).last())
    }
}
