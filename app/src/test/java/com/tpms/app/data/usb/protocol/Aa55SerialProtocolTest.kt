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
        val frame = protocol.buildTireStateFrame(tireId = 0x00, pressureRaw = 29, tempRaw = 80)
        val sensors = protocol.feed(frame, 1_000L) { null }

        assertEquals(1, sensors.size)
        val sensor = sensors.first()
        assertEquals("FL", sensor.id)
        assertEquals(29 * 3.44f, sensor.pressureKpa, 0.01f)
        assertEquals(30f, sensor.temperatureCelsius, 0.01f)
    }

    @Test
    fun feed_parsesRealDongleFrame() {
        // From live 100-a1-xl-v01 log: FL ~244 kPa, 15°C
        val frame = byteArrayOf(0x55, 0xAA, 0x08, 0x00, 0x47, 0x41, 0x00, 0xF1.toByte())
        val sensors = protocol.feed(frame, 1_000L) { null }

        assertEquals(1, sensors.size)
        assertEquals("FL", sensors.first().id)
        assertEquals(71 * 3.44f, sensors.first().pressureKpa, 0.01f)
        assertEquals(15f, sensors.first().temperatureCelsius, 0.01f)
    }

    @Test
    fun feed_parsesMultipleFramesInOneChunk() {
        val chunk = byteArrayOf(
            0x55, 0xAA, 0x08, 0x00, 0x47, 0x41, 0x00, 0xF1.toByte(),
            0x55, 0xAA, 0x08, 0x01, 0x46, 0x43, 0x00, 0xF3.toByte(),
            0x55, 0xAA, 0x08, 0x10, 0x43, 0x43, 0x00, 0xE7.toByte(),
            0x55, 0xAA, 0x08, 0x11, 0x44, 0x42, 0x00, 0xE0.toByte(),
        )
        val sensors = protocol.feed(chunk, 2_000L) { null }

        assertEquals(4, sensors.size)
        assertEquals(listOf("FL", "FR", "RL", "RR"), sensors.map { it.id })
    }

    @Test
    fun feed_mapsHardwareAlerts() {
        val frame = protocol.buildTireStateFrame(tireId = 0x01, pressureRaw = 35, tempRaw = 70, status = 0x10)
        val sensors = protocol.feed(frame, 2_000L) { null }

        assertNotNull(sensors.firstOrNull())
        assertEquals(AlertType.BATTERY_LOW, sensors.first().alertType)
    }

    @Test
    fun feed_reassemblesSplitChunks() {
        val frame = protocol.buildTireStateFrame(tireId = 0x10, pressureRaw = 30, tempRaw = 75)
        val firstChunk = frame.copyOf(4)
        val secondChunk = frame.copyOfRange(4, frame.size)

        protocol.feed(firstChunk, 3_000L) { null }
        val sensors = protocol.feed(secondChunk, 3_000L) { null }

        assertEquals(1, sensors.size)
        assertEquals("RL", sensors.first().id)
    }

    @Test
    fun feed_ignoresInvalidChecksum() {
        val frame = protocol.buildTireStateFrame(tireId = 0x11, pressureRaw = 30, tempRaw = 75)
        frame[frame.lastIndex] = 0x00
        val sensors = protocol.feed(frame, 4_000L) { null }
        assertTrue(sensors.isEmpty())
    }

    @Test
    fun feed_survivesGarbageData() {
        val garbage = ByteArray(200) { (it * 17).toByte() }
        assertTrue(protocol.feed(garbage, 1L) { null }.isEmpty())

        val frame = protocol.buildTireStateFrame(tireId = 0x00, pressureRaw = 30, tempRaw = 75)
        assertEquals(1, protocol.feed(frame, 2L) { null }.size)
    }
}
