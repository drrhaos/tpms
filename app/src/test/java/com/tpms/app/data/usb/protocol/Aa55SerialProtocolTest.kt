package com.tpms.app.data.usb.protocol

import com.tpms.app.domain.model.SensorFlags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals(
            listOf(0x55, 0xAA, 0x06, 0x19, 0x00, 0xE0),
            frame.map { it.toInt() and 0xFF }
        )
    }

    @Test
    fun buildFrame_computesChecksumForSensorIdQuery() {
        val frame = protocol.buildFrame(0x07, 0x00)
        assertEquals(
            listOf(0x55, 0xAA, 0x06, 0x07, 0x00, 0xFE),
            frame.map { it.toInt() and 0xFF }
        )
    }

    @Test
    fun feed_parsesTireStateFrame() {
        val frame = protocol.buildTireStateFrame(tireId = 0x00, pressureRaw = 29, tempRaw = 80)
        val sensors = protocol.feed(frame, 1_000L)

        assertEquals(1, sensors.size)
        val sensor = sensors.first()
        assertEquals("FL", sensor.id)
        assertEquals(29 * 3.44f, sensor.pressureKpa, 0.01f)
        assertEquals(30f, sensor.temperatureCelsius, 0.01f)
        assertNull(sensor.batteryPercent)
        assertNull(sensor.alertType)
        assertEquals(SensorFlags.NONE, sensor.flags)
    }

    @Test
    fun feed_parsesRealDongleFrame() {
        // From live 100-a1-xl-v01 log: FL ~244 kPa, 15°C
        val frame = bytes(0x55, 0xAA, 0x08, 0x00, 0x47, 0x41, 0x00, 0xF1)
        val sensors = protocol.feed(frame, 1_000L)

        assertEquals(1, sensors.size)
        assertEquals("FL", sensors.first().id)
        assertEquals(71 * 3.44f, sensors.first().pressureKpa, 0.01f)
        assertEquals(15f, sensors.first().temperatureCelsius, 0.01f)
    }

    @Test
    fun feed_parsesMultipleFramesInOneChunk() {
        val chunk = bytes(
            0x55, 0xAA, 0x08, 0x00, 0x47, 0x41, 0x00, 0xF1,
            0x55, 0xAA, 0x08, 0x01, 0x46, 0x43, 0x00, 0xF3,
            0x55, 0xAA, 0x08, 0x10, 0x43, 0x43, 0x00, 0xE7,
            0x55, 0xAA, 0x08, 0x11, 0x44, 0x42, 0x00, 0xE0,
        )
        val sensors = protocol.feed(chunk, 2_000L)

        assertEquals(listOf("FL", "FR", "RL", "RR"), sensors.map { it.id })
    }

    @Test
    fun feed_mapsStatusBitsToFlags() {
        val lowBattery = protocol.feed(
            protocol.buildTireStateFrame(tireId = 0x01, pressureRaw = 35, tempRaw = 70, status = 0x10), 1L
        ).single()
        val leak = protocol.feed(
            protocol.buildTireStateFrame(tireId = 0x10, pressureRaw = 10, tempRaw = 70, status = 0x08), 2L
        ).single()
        val noSignal = protocol.feed(
            protocol.buildTireStateFrame(tireId = 0x11, pressureRaw = 35, tempRaw = 70, status = 0x20), 3L
        ).single()

        assertEquals(SensorFlags(lowBattery = true), lowBattery.flags)
        assertEquals(SensorFlags(leak = true), leak.flags)
        assertEquals(SensorFlags(noSignal = true), noSignal.flags)
        assertNull(lowBattery.batteryPercent)
        assertNull("parser must not decide alerts", lowBattery.alertType)
    }

    @Test
    fun feed_ignoresUnconfirmedBit0x80() {
        val frame = protocol.buildTireStateFrame(tireId = 0x00, pressureRaw = 35, tempRaw = 70, status = 0x80)
        assertEquals(SensorFlags.NONE, protocol.feed(frame, 1L).single().flags)
    }

    @Test
    fun feed_dropsUnknownTireCode() {
        val unknown = protocol.buildTireStateFrame(tireId = 0x07, pressureRaw = 35, tempRaw = 70)
        val known = protocol.buildTireStateFrame(tireId = 0x05, pressureRaw = 35, tempRaw = 70)

        assertTrue(protocol.feed(unknown, 1L).isEmpty())
        assertEquals("SP", protocol.feed(known, 2L).single().id)
    }

    @Test
    fun feed_reassemblesSplitChunks() {
        val frame = protocol.buildTireStateFrame(tireId = 0x10, pressureRaw = 30, tempRaw = 75)

        protocol.feed(frame.copyOf(4), 3_000L)
        val sensors = protocol.feed(frame.copyOfRange(4, frame.size), 3_000L)

        assertEquals("RL", sensors.single().id)
    }

    @Test
    fun feed_ignoresInvalidChecksum() {
        val frame = protocol.buildTireStateFrame(tireId = 0x11, pressureRaw = 30, tempRaw = 75)
        frame[frame.lastIndex] = 0x00
        assertTrue(protocol.feed(frame, 4_000L).isEmpty())
    }

    @Test
    fun feed_resyncsByteWiseAfterBrokenFrame() {
        // A truncated frame header (bytes lost on the wire) directly followed by an intact frame:
        // the bad candidate swallows the start of the good one, so it must be skipped one byte at a time.
        val broken = bytes(0x55, 0xAA, 0x08, 0x00)
        val intact = protocol.buildTireStateFrame(tireId = 0x01, pressureRaw = 30, tempRaw = 75)

        val sensors = protocol.feed(broken + intact, 1L)

        assertEquals("FR", sensors.single().id)
    }

    @Test
    fun feed_survivesGarbageData() {
        val garbage = ByteArray(200) { (it * 17).toByte() }
        assertTrue(protocol.feed(garbage, 1L).isEmpty())

        val frame = protocol.buildTireStateFrame(tireId = 0x00, pressureRaw = 30, tempRaw = 75)
        assertEquals(1, protocol.feed(frame, 2L).size)
    }

    @Test
    fun feed_parsesSensorIdFrames() {
        val idFrame = withChecksum(0x55, 0xAA, 0x09, 0x01, 0xA1, 0xB2, 0xC3, 0xD4, 0x00)
        val emptySpare = bytes(0x55, 0xAA, 0x09, 0x05, 0x00, 0x00, 0x00, 0x05, 0xF6)

        assertTrue(protocol.feed(idFrame + emptySpare, 1L).isEmpty())

        assertEquals(
            listOf(
                Aa55AuxFrame.SensorId(slot = "FL", sensorId = "A1B2C3D4"),
                Aa55AuxFrame.SensorId(slot = "SP", sensorId = null),
            ),
            protocol.drainAuxFrames()
        )
        assertTrue(protocol.drainAuxFrames().isEmpty())
    }

    @Test
    fun feed_recognizesOtherReceiverFrames() {
        val pairOk = withChecksum(0x55, 0xAA, 0x06, 0x18, 0x00, 0x00)
        val swapAck = withChecksum(0x55, 0xAA, 0x07, 0x30, 0x00, 0x01, 0x00)

        assertTrue(protocol.feed(pairOk + swapAck, 1L).isEmpty())

        assertEquals(
            listOf("pair_ok", "swap_ack"),
            protocol.drainAuxFrames().map { (it as Aa55AuxFrame.Other).kind }
        )
    }

    private fun bytes(vararg values: Int): ByteArray = values.map { it.toByte() }.toByteArray()

    /** Builds a frame from [values] and replaces its last byte with the XOR checksum. */
    private fun withChecksum(vararg values: Int): ByteArray {
        val ints = values.copyOf()
        ints[ints.lastIndex] = protocol.checksum(ints)
        return bytes(*ints)
    }
}
