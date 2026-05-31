package com.tpms.app.data.usb

import android.util.Log
import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.TireSensor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HidProtocol @Inject constructor() {

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
        val hex = frame.bytes.joinToString(" ") { "%02X".format(it) }
        Log.d(TAG, "RAW $hex")
    }

    fun parse(frame: RawFrame, alertChecker: (TireSensor) -> AlertType?): TireSensor? {
        val raw = frame.bytes
        if (raw.size < 6) return null

        val sensorId = raw[0].toInt().let { if (it == 0) "SENSOR_%02X%02X".format(raw[0], raw[1]) else "SENSOR_%02X".format(it) }
        val flags = raw[1].toInt() and 0xFF
        val pressureRaw = ByteBuffer.wrap(byteArrayOf(raw[2], raw[3]))
            .order(ByteOrder.LITTLE_ENDIAN)
            .short
            .toInt() and 0xFFFF
        val pressureKpa = pressureRaw * 0.1f
        val tempRaw = raw[4].toInt()
        val temperatureCelsius = if (tempRaw and 0x80 != 0) tempRaw - 256 else tempRaw
        val battery = raw[5].toInt() and 0xFF

        val isBatteryLow = flags and 0x01 != 0
        val hasAlarmFlag = flags and 0x02 != 0

        val sensor = TireSensor(
            id = sensorId,
            label = "",
            pressureKpa = pressureKpa,
            temperatureCelsius = temperatureCelsius.toFloat(),
            batteryPercent = if (battery in 0..100) battery else if (isBatteryLow) 5 else 50,
            alertType = null,
            timestamp = frame.timestamp
        )

        return sensor.copy(alertType = alertChecker(sensor))
    }

    companion object {
        private const val TAG = "TPMS_HID"
    }
}
