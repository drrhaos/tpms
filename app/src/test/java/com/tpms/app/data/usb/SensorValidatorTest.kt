package com.tpms.app.data.usb

import com.tpms.app.domain.model.TireSensor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SensorValidatorTest {

    @Test
    fun sanitize_acceptsNormalSensor() {
        val sensor = TireSensor(
            id = "FL",
            label = "FL",
            pressureKpa = 240f,
            temperatureCelsius = 15f,
            batteryPercent = 80,
            alertType = null,
            timestamp = 1L
        )
        assertEquals(sensor, SensorValidator.sanitize(sensor))
    }

    @Test
    fun sanitize_rejectsOutOfRangePressure() {
        val sensor = TireSensor(
            id = "FL",
            label = "FL",
            pressureKpa = 99999f,
            temperatureCelsius = 15f,
            batteryPercent = 80,
            alertType = null,
            timestamp = 1L
        )
        assertNull(SensorValidator.sanitize(sensor))
    }

    @Test
    fun sanitize_rejectsNaN() {
        val sensor = TireSensor(
            id = "FL",
            label = "FL",
            pressureKpa = Float.NaN,
            temperatureCelsius = 15f,
            batteryPercent = 80,
            alertType = null,
            timestamp = 1L
        )
        assertNull(SensorValidator.sanitize(sensor))
    }
}
