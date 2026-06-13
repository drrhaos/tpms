package com.tpms.app.data.usb.protocol

import com.tpms.app.data.usb.UsbConnection
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeelifeProtocol @Inject constructor(
    private val aa55: Aa55SerialProtocol
) {
    fun reset() = aa55.reset()

    fun feed(
        chunk: ByteArray,
        timestamp: Long,
        alertChecker: (com.tpms.app.domain.model.TireSensor) -> com.tpms.app.domain.model.AlertType?
    ) = aa55.feed(chunk, timestamp, alertChecker)

    suspend fun sendHandshake(connection: UsbConnection) {
        for (frame in handshakeFrames()) {
            connection.write(frame)
        }
    }

    fun handshakeFrames(): List<ByteArray> = listOf(
        aa55.buildFrame(CMD_HEARTBEAT, 0x00),
        aa55.buildFrame(CMD_QUERY_ID, 0x00),
    )

    companion object {
        private const val CMD_HEARTBEAT = 0x19
        private const val CMD_QUERY_ID = 0x07
    }
}
