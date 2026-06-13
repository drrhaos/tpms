package com.tpms.app.data.usb

import com.tpms.app.data.usb.protocol.HidGenericProtocol
import com.tpms.app.domain.model.AlertType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class HidGenericProtocolTest {

    private lateinit var protocol: HidGenericProtocol

    @Before
    fun setUp() {
        protocol = HidGenericProtocol()
    }

    @Test
    fun parse_returnsNull_whenFrameTooShort() {
        assertNull(protocol.parse(byteArrayOf(0x01, 0x00, 0x03), 1_000L) { null })
    }

    @Test
    fun parse_decodesPressureTemperatureAndBattery() {
        // id=1, flags=0, pressure=1000 (0x03E8) → 100.0 kPa, temp=30°C, battery=85%
        val bytes = byteArrayOf(0x01, 0x00, 0xE8.toByte(), 0x03, 0x1E, 0x55)

        val sensor = protocol.parse(bytes, 2_000L) { null }

        assertNotNull(sensor)
        assertEquals("SENSOR_01", sensor!!.id)
        assertEquals(100.0f, sensor.pressureKpa, 0.01f)
        assertEquals(30.0f, sensor.temperatureCelsius, 0.01f)
        assertEquals(85, sensor.batteryPercent)
        assertNull(sensor.alertType)
    }

    @Test
    fun parse_decodesNegativeTemperature() {
        // temp byte 0xEC = 236 → signed -20°C
        val bytes = byteArrayOf(0x02, 0x00, 0x64, 0x00, 0xEC.toByte(), 0x32)

        val sensor = protocol.parse(bytes, 3_000L) { null }

        assertNotNull(sensor)
        assertEquals(-20.0f, sensor!!.temperatureCelsius, 0.01f)
    }

    @Test
    fun parse_appliesAlertChecker() {
        val bytes = byteArrayOf(0x03, 0x00, 0xE8.toByte(), 0x03, 0x1E, 0x55)

        val sensor = protocol.parse(bytes, 4_000L) { AlertType.LOW_PRESSURE }

        assertNotNull(sensor)
        assertEquals(AlertType.LOW_PRESSURE, sensor!!.alertType)
        assertEquals(true, sensor.isAlert)
    }

    @Test
    fun parse_usesBatteryLowFlagWhenPercentOutOfRange() {
        val bytes = byteArrayOf(0x01, 0x01, 0xE8.toByte(), 0x03, 0x1E, 0xFF.toByte())

        val sensor = protocol.parse(bytes, 5_000L) { null }

        assertNotNull(sensor)
        assertEquals(5, sensor!!.batteryPercent)
    }
}
