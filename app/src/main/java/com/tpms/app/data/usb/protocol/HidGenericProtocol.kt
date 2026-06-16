package com.tpms.app.data.usb.protocol

import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.TireSensor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HidGenericProtocol @Inject constructor() {

    fun parse(
        raw: ByteArray,
        timestamp: Long,
        alertChecker: (TireSensor) -> AlertType?
    ): TireSensor? {
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
            val isBatteryLow = flags and 0x01 != 0

            if (!pressureKpa.isFinite() || !temperatureCelsius.isFinite()) return null

            val sensor = TireSensor(
                id = sensorId,
                label = "",
                pressureKpa = pressureKpa,
                temperatureCelsius = temperatureCelsius,
                batteryPercent = if (battery in 0..100) battery else if (isBatteryLow) 5 else 50,
                alertType = null,
                timestamp = timestamp
            )
            val alert = runCatching { alertChecker(sensor) }.getOrNull()
            sensor.copy(alertType = alert)
        } catch (_: Exception) {
            null
        }
    }
}
