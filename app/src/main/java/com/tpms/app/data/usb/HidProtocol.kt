package com.tpms.app.data.usb

import com.tpms.app.data.usb.protocol.HidGenericProtocol
import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.TireSensor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HidProtocol @Inject constructor(
    private val delegate: HidGenericProtocol
) {

    data class RawFrame(
        val bytes: ByteArray,
        val timestamp: Long
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is RawFrame) return false
            return bytes.contentEquals(other.bytes) && timestamp == other.timestamp
        }

        override fun hashCode(): Int = bytes.contentHashCode() * 31 + timestamp.hashCode()
    }

    fun logFrame(frame: RawFrame) {
        // Logging handled by TpmsProtocolRouter
    }

    fun parse(frame: RawFrame, alertChecker: (TireSensor) -> AlertType?): TireSensor? =
        delegate.parse(frame.bytes, frame.timestamp, alertChecker)
}
