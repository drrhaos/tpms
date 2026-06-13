package com.tpms.app.data.usb.protocol

import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.TireSensor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Aa55SerialProtocol @Inject constructor() {

    private val buffer = mutableListOf<Byte>()

    fun reset() {
        buffer.clear()
    }

    fun feed(
        chunk: ByteArray,
        timestamp: Long,
        alertChecker: (TireSensor) -> AlertType?
    ): List<TireSensor> {
        buffer.addAll(chunk.toList())
        val sensors = mutableListOf<TireSensor>()
        while (true) {
            val frame = extractFrame() ?: break
            parseFrame(frame, timestamp, alertChecker)?.let { sensors.add(it) }
        }
        return sensors
    }

    fun buildFrame(command: Int, vararg payload: Int): ByteArray {
        val length = 3 + 1 + payload.size + 1
        val frame = IntArray(length)
        frame[0] = HEADER_0
        frame[1] = HEADER_1
        frame[2] = length
        frame[3] = command
        payload.forEachIndexed { index, value -> frame[4 + index] = value }
        frame[length - 1] = checksum(frame)
        return frame.map { it.toByte() }.toByteArray()
    }

    fun checksum(frame: IntArray): Int {
        val length = frame[2]
        var result = frame[0]
        for (i in 1 until length - 1) {
            result = result xor frame[i]
        }
        return result and 0xFF
    }

    private fun extractFrame(): ByteArray? {
        while (buffer.size >= 3) {
            if (buffer[0] != HEADER_0.toByte() || buffer[1] != HEADER_1.toByte()) {
                buffer.removeAt(0)
                continue
            }
            val length = buffer[2].toInt() and 0xFF
            if (length < 4 || length > MAX_FRAME_SIZE) {
                buffer.removeAt(0)
                continue
            }
            if (buffer.size < length) return null
            val frame = ByteArray(length) { buffer[it] }
            repeat(length) { buffer.removeAt(0) }
            if (!isValidChecksum(frame)) continue
            return frame
        }
        return null
    }

    private fun isValidChecksum(frame: ByteArray): Boolean {
        if (frame.size < 4) return false
        val ints = frame.map { it.toInt() and 0xFF }
        val expected = checksum(ints.toIntArray())
        return (ints.last() and 0xFF) == expected
    }

    private fun parseFrame(
        frame: ByteArray,
        timestamp: Long,
        alertChecker: (TireSensor) -> AlertType?
    ): TireSensor? {
        val ints = frame.map { it.toInt() and 0xFF }
        if (ints.size < 4) return null

        val hasCommand = ints[3] == CMD_TIRE_STATE
        val minSize = if (hasCommand) 7 else 6
        if (ints.size < minSize) return null

        val offset = if (hasCommand) 1 else 0
        val tireCode = ints[3 + offset]
        val pressureKpa = ints[4 + offset] * PRESSURE_SCALE_KPA
        val temperatureCelsius = (ints[5 + offset] - TEMP_OFFSET).toFloat()
        val status = ints.getOrElse(6 + offset) { 0 }

        val hardwareAlert = hardwareAlert(status)
        val label = tireLabel(tireCode)
        val sensor = TireSensor(
            id = label,
            label = label,
            pressureKpa = pressureKpa,
            temperatureCelsius = temperatureCelsius,
            batteryPercent = if (status and STATUS_LOW_BATTERY != 0) 5 else 50,
            alertType = null,
            timestamp = timestamp
        )
        val thresholdAlert = alertChecker(sensor)
        return sensor.copy(alertType = hardwareAlert ?: thresholdAlert)
    }

    private fun hardwareAlert(status: Int): AlertType? = when {
        status and STATUS_NO_SIGNAL != 0 -> AlertType.SENSOR_LOST
        status and STATUS_LEAKAGE != 0 -> AlertType.LOW_PRESSURE
        status and STATUS_LOW_BATTERY != 0 -> AlertType.BATTERY_LOW
        else -> null
    }

    private fun tireLabel(code: Int): String = when (code) {
        TIRE_FL -> "FL"
        TIRE_FR -> "FR"
        TIRE_RL -> "RL"
        TIRE_RR -> "RR"
        TIRE_SPARE -> "SP"
        else -> "SENSOR_%02X".format(code)
    }

    companion object {
        const val HEADER_0 = 0x55
        const val HEADER_1 = 0xAA
        const val CMD_TIRE_STATE = 0x08
        const val PRESSURE_SCALE_KPA = 3.44f
        const val TEMP_OFFSET = 50
        private const val MAX_FRAME_SIZE = 64

        private const val TIRE_FL = 0x00
        private const val TIRE_FR = 0x01
        private const val TIRE_RL = 0x10
        private const val TIRE_RR = 0x11
        private const val TIRE_SPARE = 0x05

        private const val STATUS_NO_SIGNAL = 0x80
        private const val STATUS_LEAKAGE = 0x08
        private const val STATUS_LOW_BATTERY = 0x10
    }
}
