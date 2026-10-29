package com.tpms.app.data.usb.protocol

import com.tpms.app.data.usb.toHexPreview
import com.tpms.app.domain.model.SensorFlags
import com.tpms.app.domain.model.TireSensor

/** Receiver frames that are not tire readings. */
sealed interface Aa55AuxFrame {
    /** Answer to the sensor-ID query (`0x07`); [sensorId] is null when the slot has no sensor. */
    data class SensorId(val slot: String, val sensorId: String?) : Aa55AuxFrame

    data class Other(val kind: String, val hex: String) : Aa55AuxFrame
}

/**
 * Stateful 55AA stream parser. One instance per open dongle: the reassembly buffer must not
 * survive a reconnect.
 */
class Aa55SerialProtocol {

    private val buffer = mutableListOf<Byte>()
    private val auxFrames = mutableListOf<Aa55AuxFrame>()

    /** Returns tire readings; alerts are left to the caller. Other frames go to [drainAuxFrames]. */
    fun feed(chunk: ByteArray, timestamp: Long): List<TireSensor> {
        if (chunk.isEmpty()) return emptyList()
        return try {
            buffer.addAll(chunk.toList())
            trimBufferIfNeeded()

            val sensors = mutableListOf<TireSensor>()
            var framesParsed = 0
            while (framesParsed < MAX_FRAMES_PER_CHUNK) {
                val frame = extractFrame() ?: break
                framesParsed++
                handleFrame(frame, timestamp)?.let { sensors.add(it) }
            }
            sensors
        } catch (_: Exception) {
            buffer.clear()
            emptyList()
        }
    }

    fun drainAuxFrames(): List<Aa55AuxFrame> {
        if (auxFrames.isEmpty()) return emptyList()
        val drained = auxFrames.toList()
        auxFrames.clear()
        return drained
    }

    /** Command frame (length 6): 55 AA 06 CMD PAYLOAD CHK */
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

    /** Tire state frame (length 8): 55 AA 08 TIRE PRESS TEMP STATUS CHK */
    fun buildTireStateFrame(
        tireId: Int,
        pressureRaw: Int,
        tempRaw: Int,
        status: Int = 0
    ): ByteArray {
        val frame = intArrayOf(HEADER_0, HEADER_1, FRAME_TIRE_STATE, tireId, pressureRaw, tempRaw, status, 0)
        frame[7] = checksum(frame)
        return frame.map { it.toByte() }.toByteArray()
    }

    fun checksum(frame: IntArray): Int {
        if (frame.size < 3) return 0
        val length = frame[2]
        if (length < 2 || length > frame.size) return 0
        var result = frame[0]
        for (i in 1 until length - 1) {
            result = result xor frame[i]
        }
        return result and 0xFF
    }

    private fun trimBufferIfNeeded() {
        if (buffer.size > MAX_BUFFER_SIZE) {
            buffer.subList(0, buffer.size - MAX_BUFFER_SIZE / 2).clear()
        }
    }

    /**
     * Finds the next frame with a valid checksum. A candidate that fails the checksum is skipped
     * one byte at a time: after lost bytes, a broken header can cover the start of an intact frame.
     */
    private fun extractFrame(): ByteArray? {
        var guard = 0
        while (buffer.size >= 3 && guard++ < MAX_SCAN_BYTES) {
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
            if (!isValidChecksum(frame)) {
                buffer.removeAt(0)
                continue
            }
            buffer.subList(0, length).clear()
            return frame
        }
        if (guard >= MAX_SCAN_BYTES) buffer.clear()
        return null
    }

    private fun isValidChecksum(frame: ByteArray): Boolean {
        if (frame.size < 4) return false
        val ints = frame.map { it.toInt() and 0xFF }
        return ints.last() == checksum(ints.toIntArray())
    }

    private fun handleFrame(frame: ByteArray, timestamp: Long): TireSensor? {
        val ints = frame.map { it.toInt() and 0xFF }
        return when {
            ints[2] == FRAME_TIRE_STATE && ints.size >= 8 -> parseTireState(ints, timestamp)
            ints[2] == FRAME_SENSOR_ID && ints.size >= 9 -> {
                parseSensorId(ints)?.let { auxFrames.add(it) }
                null
            }
            else -> {
                auxFrames.add(Aa55AuxFrame.Other(otherKind(ints), frame.toHexPreview()))
                null
            }
        }
    }

    private fun parseTireState(ints: List<Int>, timestamp: Long): TireSensor? {
        val label = TIRE_CODES[ints[3]] ?: return null
        val status = ints[6]
        return TireSensor(
            id = label,
            label = label,
            pressureKpa = ints[4] * PRESSURE_SCALE_KPA,
            temperatureCelsius = (ints[5] - TEMP_OFFSET).toFloat(),
            batteryPercent = null,
            alertType = null,
            timestamp = timestamp,
            flags = SensorFlags(
                noSignal = status and STATUS_NO_SIGNAL != 0,
                leak = status and STATUS_LEAKAGE != 0,
                lowBattery = status and STATUS_LOW_BATTERY != 0
            )
        )
    }

    /** `55 AA 09 N ID0 ID1 ID2 ID3 CHK`, where N counts slots 1..5 instead of using tire codes. */
    private fun parseSensorId(ints: List<Int>): Aa55AuxFrame.SensorId? {
        val slot = ID_SLOTS[ints[3]] ?: return null
        val idBytes = ints.subList(4, 8)
        val empty = idBytes.take(3).all { it == 0 }
        val sensorId = if (empty) null else idBytes.joinToString("") { "%02X".format(it) }
        return Aa55AuxFrame.SensorId(slot, sensorId)
    }

    private fun otherKind(ints: List<Int>): String = when {
        ints[2] == 6 && ints[3] == 0x18 -> "pair_ok"
        ints[2] == 6 && ints[3] == 0x00 && ints[4] == 0x88 -> "handshake_request"
        ints[2] == 6 && ints[3] == 0xB5 -> "ack_time"
        ints[2] == 7 && ints[3] == 0x30 -> "swap_ack"
        else -> "unknown"
    }

    companion object {
        const val HEADER_0 = 0x55
        const val HEADER_1 = 0xAA
        const val FRAME_TIRE_STATE = 0x08
        const val FRAME_SENSOR_ID = 0x09
        const val CMD_HEARTBEAT = 0x19
        const val CMD_QUERY_ID = 0x07
        const val PRESSURE_SCALE_KPA = 3.44f
        const val TEMP_OFFSET = 50
        private const val MAX_FRAME_SIZE = 64
        private const val MAX_BUFFER_SIZE = 512
        private const val MAX_FRAMES_PER_CHUNK = 16
        private const val MAX_SCAN_BYTES = 256

        private val TIRE_CODES = mapOf(
            0x00 to "FL",
            0x01 to "FR",
            0x10 to "RL",
            0x11 to "RR",
            0x05 to "SP",
        )

        private val ID_SLOTS = mapOf(
            0x01 to "FL",
            0x02 to "FR",
            0x03 to "RL",
            0x04 to "RR",
            0x05 to "SP",
        )

        // docs/research/aa55-status-flags.md §1.3: "no signal" is 0x20; 0x80 is not confirmed.
        private const val STATUS_NO_SIGNAL = 0x20
        private const val STATUS_LEAKAGE = 0x08
        private const val STATUS_LOW_BATTERY = 0x10
    }
}
