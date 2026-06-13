package com.tpms.app.data.usb

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
    private val deelifeProtocol: DeelifeProtocol,
    private val debugLog: UsbDebugLog
) {
    private var activeProtocol: DongleProtocol? = null

    fun setActiveProtocol(protocol: DongleProtocol) {
        if (activeProtocol != protocol) {
            aa55Protocol.reset()
            deelifeProtocol.reset()
            activeProtocol = protocol
            debugLog.usb(TAG, "Active protocol: ${protocol.displayName}")
        }
    }

    fun activeProtocol(): DongleProtocol? = activeProtocol

    suspend fun onDongleOpened(protocol: DongleProtocol, connection: UsbConnection) {
        setActiveProtocol(protocol)
        runCatching {
            when (protocol) {
                DongleProtocol.DEELIFE -> deelifeProtocol.sendHandshake(connection)
                DongleProtocol.SERIAL_AA55 -> sendSerialKickstart(connection)
                DongleProtocol.HID_GENERIC -> Unit
            }
        }.onFailure { e ->
            debugLog.warn(TAG, "Handshake failed: ${e.message}")
        }
    }

    private suspend fun sendSerialKickstart(connection: UsbConnection) {
        val heartbeat = aa55Protocol.buildFrame(0x19, 0x00)
        connection.write(heartbeat)
        debugLog.usb(TAG, "Sent serial heartbeat kickstart")
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
        if (raw.isEmpty()) return emptyList()
        val protocol = activeProtocol ?: return emptyList()
        return try {
            logFrame(protocol, raw)
            val parsed = when (protocol) {
                DongleProtocol.HID_GENERIC -> {
                    hidProtocol.parse(raw, timestamp, alertChecker)?.let { listOf(it) } ?: emptyList()
                }
                DongleProtocol.SERIAL_AA55 -> aa55Protocol.feed(raw, timestamp, alertChecker)
                DongleProtocol.DEELIFE -> deelifeProtocol.feed(raw, timestamp, alertChecker)
            }
            parsed.mapNotNull { SensorValidator.sanitize(it) }
        } catch (e: Exception) {
            debugLog.warn(TAG, "Parse error (${protocol.displayName}): ${e.message}")
            emptyList()
        }
    }

    private fun logFrame(protocol: DongleProtocol, raw: ByteArray) {
        val preview = raw.take(32).joinToString(" ") { "%02X".format(it) }
        val suffix = if (raw.size > 32) " …(+${raw.size - 32}b)" else ""
        debugLog.raw(TAG, "${protocol.displayName} $preview$suffix")
    }

    companion object {
        private const val TAG = "TPMS_RAW"
    }
}
