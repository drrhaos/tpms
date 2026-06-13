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

    @Test
    fun sanitize_rejectsEmptySpareSlot() {
        val sensor = TireSensor(
            id = "SP",
            label = "SP",
            pressureKpa = 55f,
            temperatureCelsius = -34f,
            batteryPercent = 50,
            alertType = null,
            timestamp = 1L
        )
        assertNull(SensorValidator.sanitize(sensor))
    }

    @Test
    fun sanitize_acceptsSpareAtMinimumLivePressure() {
        val sensor = TireSensor(
            id = "SP",
            label = "SP",
            pressureKpa = 100f,
            temperatureCelsius = 20f,
            batteryPercent = 50,
            alertType = null,
            timestamp = 1L
        )
        assertEquals(sensor, SensorValidator.sanitize(sensor))
    }

    @Test
    fun sanitize_rejectsBlankId() {
        val sensor = TireSensor(
            id = "  ",
            pressureKpa = 240f,
            temperatureCelsius = 20f,
            batteryPercent = 80,
            alertType = null,
            timestamp = 1L
        )
        assertNull(SensorValidator.sanitize(sensor))
    }

    @Test
    fun sanitize_coercesBatteryPercent() {
        val sensor = TireSensor(
            id = "FL",
            pressureKpa = 240f,
            temperatureCelsius = 20f,
            batteryPercent = 150,
            alertType = null,
            timestamp = 1L
        )
        assertEquals(100, SensorValidator.sanitize(sensor)?.batteryPercent)
    }
}
