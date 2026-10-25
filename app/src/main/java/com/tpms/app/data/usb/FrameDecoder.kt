package com.tpms.app.data.usb

import com.tpms.app.data.settings.SensorValidatorDefaults
import com.tpms.app.data.usb.protocol.Aa55AuxFrame
import com.tpms.app.data.usb.protocol.Aa55SerialProtocol
import com.tpms.app.data.usb.protocol.HidGenericProtocol
import com.tpms.app.domain.model.DongleProtocol
import com.tpms.app.domain.model.TireSensor

/** Wheel placement (spare toggle, mapping) is not decoded here: the repository's WheelAssignment decides. */
data class DecodeSettings(
    val minLiveWheelPressureKpa: Float = SensorValidatorDefaults.MIN_LIVE_WHEEL_PRESSURE_KPA
)

data class DecodeResult(
    val sensors: List<TireSensor>,
    val auxFrames: List<Aa55AuxFrame> = emptyList()
) {
    companion object {
        val EMPTY = DecodeResult(emptyList())
    }
}

/**
 * Turns raw USB chunks into validated, confirmed readings. Alerts are applied later, by the
 * repository, because they need history that outlives a connection.
 * Holds per-connection state (reassembly buffer, confirmation history): create one per open dongle.
 */
class FrameDecoder(
    val protocol: DongleProtocol,
    private val confirmation: ReadingConfirmationFilter = ReadingConfirmationFilter()
) {
    private val aa55 = Aa55SerialProtocol()
    private val hid = HidGenericProtocol()

    fun decode(raw: ByteArray, timestamp: Long, settings: DecodeSettings): DecodeResult {
        if (raw.isEmpty()) return DecodeResult.EMPTY
        val parsed: List<TireSensor>
        val aux: List<Aa55AuxFrame>
        when (protocol) {
            DongleProtocol.HID_GENERIC -> {
                parsed = listOfNotNull(hid.parse(raw, timestamp))
                aux = emptyList()
            }
            DongleProtocol.SERIAL_AA55, DongleProtocol.DEELIFE -> {
                parsed = aa55.feed(raw, timestamp)
                aux = aa55.drainAuxFrames()
            }
        }
        val sensors = parsed
            .mapNotNull { SensorValidator.sanitize(it, settings.minLiveWheelPressureKpa) }
            .mapNotNull { confirmation.offer(it) }
        return DecodeResult(sensors, aux)
    }

    /** Frames to send right after opening: heartbeat kick-start and sensor-ID query. */
    fun handshakeFrames(): List<ByteArray> = when (protocol) {
        DongleProtocol.HID_GENERIC -> emptyList()
        DongleProtocol.SERIAL_AA55, DongleProtocol.DEELIFE -> listOf(
            aa55.buildFrame(Aa55SerialProtocol.CMD_HEARTBEAT, 0x00),
            aa55.buildFrame(Aa55SerialProtocol.CMD_QUERY_ID, 0x00),
        )
    }
}
