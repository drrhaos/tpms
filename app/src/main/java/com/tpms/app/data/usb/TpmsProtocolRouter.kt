package com.tpms.app.data.usb

import android.util.Log
import com.tpms.app.data.usb.protocol.Aa55SerialProtocol
import com.tpms.app.data.usb.protocol.DeelifeProtocol
import com.tpms.app.data.usb.protocol.HidGenericProtocol
import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.DongleProtocol
import com.tpms.app.domain.model.TireSensor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TpmsProtocolRouter @Inject constructor(
    private val hidProtocol: HidGenericProtocol,
    private val aa55Protocol: Aa55SerialProtocol,
    private val deelifeProtocol: DeelifeProtocol
) {
    private var activeProtocol: DongleProtocol? = null

    fun setActiveProtocol(protocol: DongleProtocol) {
        if (activeProtocol != protocol) {
            aa55Protocol.reset()
            deelifeProtocol.reset()
            activeProtocol = protocol
            Log.d(TAG, "Active protocol: ${protocol.displayName}")
        }
    }

    fun activeProtocol(): DongleProtocol? = activeProtocol

    suspend fun onDongleOpened(protocol: DongleProtocol, connection: UsbConnection) {
        setActiveProtocol(protocol)
        if (protocol == DongleProtocol.DEELIFE) {
            deelifeProtocol.sendHandshake(connection)
        }
    }

    fun onDongleClosed() {
        aa55Protocol.reset()
        deelifeProtocol.reset()
        activeProtocol = null
    }

    fun parse(
        raw: ByteArray,
        timestamp: Long,
        alertChecker: (TireSensor) -> AlertType?
    ): List<TireSensor> {
        val protocol = activeProtocol ?: return emptyList()
        logFrame(protocol, raw)
        return when (protocol) {
            DongleProtocol.HID_GENERIC -> {
                hidProtocol.parse(raw, timestamp, alertChecker)?.let { listOf(it) } ?: emptyList()
            }
            DongleProtocol.SERIAL_AA55 -> aa55Protocol.feed(raw, timestamp, alertChecker)
            DongleProtocol.DEELIFE -> deelifeProtocol.feed(raw, timestamp, alertChecker)
        }
    }

    private fun logFrame(protocol: DongleProtocol, raw: ByteArray) {
        val hex = raw.joinToString(" ") { "%02X".format(it) }
        Log.d(TAG, "${protocol.displayName} RAW $hex")
    }

    companion object {
        private const val TAG = "TPMS_RAW"
    }
}
