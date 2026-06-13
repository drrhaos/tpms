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
    fun orderedValues_prefersLabelMatch() {
        val sensors = mapOf(
            "SENSOR_01" to sensor("SENSOR_01", "FL"),
            "SENSOR_02" to sensor("SENSOR_02", "FR")
        )

        val values = WheelLayout.orderedValues(sensors)

        assertEquals(listOf("SENSOR_01", "SENSOR_02"), values.map { it.id })
    }
}
