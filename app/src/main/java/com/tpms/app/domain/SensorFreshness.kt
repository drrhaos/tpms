package com.tpms.app.domain

import com.tpms.app.domain.model.DongleProtocol
import com.tpms.app.domain.model.TireSensor

/**
 * "Old data" is a state, not an alert (wayfinder 10).
 *
 * - Per wheel: only HID has an honest [TireSensor.timestamp]; a wheel is stale when its sensor has not
 *   transmitted for more than [HID_STALE_AFTER_MS]. AA55/Deelife receivers repeat their slot cache, so
 *   the timestamp is the frame arrival time — those wheels are never stale here; a dead sensor is
 *   flagged by the receiver itself (0x20 → SENSOR_LOST).
 * - Overall ("данные устарели"): judged by the connection, not by sensor ages — stale when there is no
 *   live session (reconnecting, dongle gone, data restored from a snapshot), or when a continuously
 *   streaming session (AA55/Deelife) has produced no valid frame for more than [FRAMES_STALE_AFTER_MS].
 *   Empty data is never outdated: with no readings at all the header says why instead.
 */
object SensorFreshness {

    const val HID_STALE_AFTER_MS = 15 * 60_000L
    const val FRAMES_STALE_AFTER_MS = 60_000L

    /**
     * Inputs for [isDataStale]; [protocol] is the live session's protocol (null when unknown),
     * [hasData] whether any sensor reading (live or restored) is shown.
     */
    data class ConnectionSnapshot(
        val hasData: Boolean,
        val hasLiveSession: Boolean,
        val protocol: DongleProtocol?,
        val sessionOpenedAtMs: Long,
        val lastValidFrameAtMs: Long
    )

    fun isWheelStale(sensor: TireSensor, protocol: DongleProtocol?, now: Long): Boolean =
        protocol?.streamsContinuously == false && now - sensor.timestamp > HID_STALE_AFTER_MS

    /** Whole minutes since the sensor's last reading, for the "N мин" caption; never negative. */
    fun ageMinutes(sensor: TireSensor, now: Long): Long =
        (now - sensor.timestamp).coerceAtLeast(0L) / 60_000L

    /**
     * Minutes for a stale wheel's "N мин" caption, or null when the wheel is fresh (or has no sensor).
     * The caption is shown even when an active alert keeps the wheel's alert colour.
     */
    fun staleWheelMinutes(sensor: TireSensor?, protocol: DongleProtocol?, now: Long): Long? =
        if (sensor != null && isWheelStale(sensor, protocol, now)) ageMinutes(sensor, now) else null

    /** A stale wheel is drawn muted unless an active alert keeps its alert colour. */
    fun isWheelMuted(sensor: TireSensor?, protocol: DongleProtocol?, now: Long): Boolean =
        sensor != null && !sensor.isAlert && isWheelStale(sensor, protocol, now)

    fun isDataStale(connection: ConnectionSnapshot, now: Long): Boolean {
        if (!connection.hasData) return false
        if (!connection.hasLiveSession) return true
        if (connection.protocol?.streamsContinuously != true) return false
        val referenceMs = maxOf(connection.sessionOpenedAtMs, connection.lastValidFrameAtMs)
        return now - referenceMs > FRAMES_STALE_AFTER_MS
    }
}
