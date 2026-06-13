package com.tpms.app.data.usb.protocol

import com.tpms.app.domain.model.AlertType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Aa55SerialProtocolTest {

    private lateinit var protocol: Aa55SerialProtocol

    @Before
    fun setUp() {
        protocol = Aa55SerialProtocol()
    }

    @Test
    fun buildFrame_computesChecksumForHeartbeat() {
        val frame = protocol.buildFrame(0x19, 0x00)
        assertEquals(6, frame.size)
        assertEquals(0x55.toByte(), frame[0])
        assertEquals(0xAA.toByte(), frame[1])
        assertEquals(0x06.toByte(), frame[2])
        assertEquals(0x19.toByte(), frame[3])
        assertEquals(0x00.toByte(), frame[4])
        assertEquals(0xE0.toByte(), frame[5])
    }

    @Test
    fun feed_parsesTireStateFrame() {
        // FL tire, pressure raw 29 → 99.76 kPa, temp 80-50=30°C, status OK
        val frame = protocol.buildFrame(0x08, 0x00, 29, 80, 0x00)
        val sensors = protocol.feed(frame, 1_000L) { null }

        assertEquals(1, sensors.size)
        val sensor = sensors.first()
        assertEquals("FL", sensor.id)
        assertEquals(29 * 3.44f, sensor.pressureKpa, 0.01f)
        assertEquals(30f, sensor.temperatureCelsius, 0.01f)
    }

    @Test
    fun feed_mapsHardwareAlerts() {
        val frame = protocol.buildFrame(0x08, 0x01, 35, 70, 0x10)
        val sensors = protocol.feed(frame, 2_000L) { null }

        assertNotNull(sensors.firstOrNull())
        assertEquals(AlertType.BATTERY_LOW, sensors.first().alertType)
    }

    @Test
    fun feed_reassemblesSplitChunks() {
        val frame = protocol.buildFrame(0x08, 0x10, 30, 75, 0x00)
        val firstChunk = frame.copyOf(4)
        val secondChunk = frame.copyOfRange(4, frame.size)

        protocol.feed(firstChunk, 3_000L) { null }
        val sensors = protocol.feed(secondChunk, 3_000L) { null }

        assertEquals(1, sensors.size)
        assertEquals("RL", sensors.first().id)
    }

    @Test
    fun feed_parsesNoCommandFormat() {
        // No-command format: 55 AA 08 <tire_id> <pressure> <temp> <status> <checksum>
        // FL tire (0x00), pressure=70 raw (240.8 kPa), temp=64 (14°C), status=OK
        val frame = byteArrayOf(
            0x55, 0xAA, 0x08, 0x00, 0x46, 0x40, 0x00, 0xF1
        )
        val sensors = protocol.feed(frame, 1_000L) { null }

        assertEquals(1, sensors.size)
        val sensor = sensors.first()
        assertEquals("FL", sensor.id)
        assertEquals(70 * 3.44f, sensor.pressureKpa, 0.01f)
        assertEquals(14f, sensor.temperatureCelsius, 0.01f)
    }

    @Test
    fun feed_parsesNoCommandFormatWithAlert() {
        // Spare tire (0x05), pressure=13 raw, temp=13 ( -37°C), status=LEAKAGE (0x08)
        val frame = byteArrayOf(
            0x55, 0xAA, 0x08, 0x05, 0x0D, 0x0D, 0x08, 0xFA.toByte()
        )
        val sensors = protocol.feed(frame, 2_000L) { null }

        assertEquals(1, sensors.size)
        val sensor = sensors.first()
        assertEquals("SP", sensor.id)
        assertEquals(AlertType.LOW_PRESSURE, sensor.alertType)
    }

    @Test
    fun feed_ignoresInvalidChecksum() {
        val frame = protocol.buildFrame(0x08, 0x11, 30, 75, 0x00)
        frame[frame.lastIndex] = 0x00
        val sensors = protocol.feed(frame, 4_000L) { null }
        assertTrue(sensors.isEmpty())
    }
}
