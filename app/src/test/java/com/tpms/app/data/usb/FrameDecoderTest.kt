package com.tpms.app.data.usb

import com.tpms.app.data.usb.protocol.Aa55AuxFrame
import com.tpms.app.data.usb.protocol.Aa55SerialProtocol
import com.tpms.app.domain.model.DongleProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameDecoderTest {

    private val frames = Aa55SerialProtocol()
    private val settings = DecodeSettings()

    @Test
    fun aa55_readingIsReportedOnlyAfterRepeat() {
        val decoder = FrameDecoder(DongleProtocol.SERIAL_AA55)
        val frame = frames.buildTireStateFrame(tireId = 0x00, pressureRaw = 70, tempRaw = 70)

        assertTrue(decoder.decode(frame, 0L, settings).sensors.isEmpty())
        assertEquals("FL", decoder.decode(frame, 500L, settings).sensors.single().id)
    }

    @Test
    fun aa55_leavesAlertsToTheRepository() {
        val decoder = FrameDecoder(DongleProtocol.SERIAL_AA55)
        // 30 * 3.44 = 103 kPa, well below any low-pressure threshold
        val frame = frames.buildTireStateFrame(tireId = 0x01, pressureRaw = 30, tempRaw = 70)

        decoder.decode(frame, 0L, settings)
        val sensor = decoder.decode(frame, 500L, settings).sensors.single()

        assertNull(sensor.alertType)
    }

    @Test
    fun aa55_keepsNoSignalFlag() {
        val decoder = FrameDecoder(DongleProtocol.SERIAL_AA55)
        val frame = frames.buildTireStateFrame(tireId = 0x10, pressureRaw = 70, tempRaw = 70, status = 0x20)

        decoder.decode(frame, 0L, settings)
        assertTrue(decoder.decode(frame, 500L, settings).sensors.single().flags.noSignal)
    }

    @Test
    fun aa55_dropsSpareWhenDisplayDisabled() {
        val decoder = FrameDecoder(DongleProtocol.SERIAL_AA55)
        val spare = frames.buildTireStateFrame(tireId = 0x05, pressureRaw = 70, tempRaw = 70)

        decoder.decode(spare, 0L, settings)
        assertTrue(decoder.decode(spare, 500L, settings).sensors.isEmpty())
    }

    @Test
    fun aa55_returnsSensorIdFrames() {
        val decoder = FrameDecoder(DongleProtocol.SERIAL_AA55)
        val emptySpare = byteArrayOf(0x55, 0xAA.toByte(), 0x09, 0x05, 0x00, 0x00, 0x00, 0x05, 0xF6.toByte())

        val result = decoder.decode(emptySpare, 0L, settings)

        assertEquals(listOf(Aa55AuxFrame.SensorId("SP", null)), result.auxFrames)
    }

    @Test
    fun handshake_sendsHeartbeatAndIdQueryForSerialProtocols() {
        val expected = listOf(
            listOf(0x55, 0xAA, 0x06, 0x19, 0x00, 0xE0),
            listOf(0x55, 0xAA, 0x06, 0x07, 0x00, 0xFE),
        )
        for (protocol in listOf(DongleProtocol.SERIAL_AA55, DongleProtocol.DEELIFE)) {
            val handshake = FrameDecoder(protocol).handshakeFrames()
            assertEquals(expected, handshake.map { frame -> frame.map { it.toInt() and 0xFF } })
        }
        assertTrue(FrameDecoder(DongleProtocol.HID_GENERIC).handshakeFrames().isEmpty())
    }

    @Test
    fun hid_readingIsConfirmedToo() {
        val decoder = FrameDecoder(DongleProtocol.HID_GENERIC)
        val report = byteArrayOf(0x01, 0x00, 0xD8.toByte(), 0x09, 0x1E, 0x55) // 0x09D8 = 2520 → 252.0 kPa

        assertTrue(decoder.decode(report, 0L, settings).sensors.isEmpty())
        assertEquals(252.0f, decoder.decode(report, 500L, settings).sensors.single().pressureKpa, 0.01f)
    }
}
