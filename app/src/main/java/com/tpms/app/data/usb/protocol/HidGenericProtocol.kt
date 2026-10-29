package com.tpms.app.data.usb.protocol

import com.tpms.app.domain.model.SensorFlags
import com.tpms.app.domain.model.TireSensor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Stateless HID report parser; alerts are left to the caller. */
class HidGenericProtocol {

    fun parse(raw: ByteArray, timestamp: Long): TireSensor? {
        if (raw.size < 6) return null
        return try {
            val sensorId = when {
                raw[0].toInt() and 0xFF == 0 && raw.size >= 2 ->
                    "SENSOR_%02X%02X".format(raw[0], raw[1])
                else -> "SENSOR_%02X".format(raw[0])
            }
            val flags = raw[1].toInt() and 0xFF
            val pressureRaw = ByteBuffer.wrap(byteArrayOf(raw[2], raw[3]))
                .order(ByteOrder.LITTLE_ENDIAN)
                .short
                .toInt() and 0xFFFF
            val pressureKpa = pressureRaw * 0.1f
            val tempRaw = raw[4].toInt() and 0xFF
            val temperatureCelsius = (if (tempRaw and 0x80 != 0) tempRaw - 256 else tempRaw).toFloat()
            val battery = raw[5].toInt() and 0xFF

            if (!pressureKpa.isFinite() || !temperatureCelsius.isFinite()) return null

            TireSensor(
                id = sensorId,
                label = "",
                pressureKpa = pressureKpa,
                temperatureCelsius = temperatureCelsius,
                batteryPercent = battery.takeIf { it in 0..100 },
                alertType = null,
                timestamp = timestamp,
                flags = SensorFlags(lowBattery = flags and 0x01 != 0)
            )
        } catch (_: Exception) {
            null
        }
    }
}
