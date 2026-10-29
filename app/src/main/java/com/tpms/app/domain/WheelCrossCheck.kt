package com.tpms.app.domain

import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.TireSensor

/**
 * Alerts a single sensor cannot raise on its own, found by comparing it with the other wheels.
 * Pure; applied by the repository after [AlertEvaluator], and never over a wheel's own ALERT.
 *
 * - **Lost sensor** (SENSOR_LOST): the sensor's value has not changed for a long time while the
 *   other road wheels keep changing (the car is moving, so the receiver works and the sensors
 *   transmit). A blowout can destroy the sensor: without this no alert would ever come.
 *   - HID: "changed" = a transmission (honest timestamp): silent ≥ [HID_LOST_AFTER_MS] while at
 *     least [HID_MIN_ACTIVE_PEERS] others transmitted within [HID_PEER_WINDOW_MS].
 *   - AA55/Deelife: the receiver repeats its slot cache forever, so a dead sensor freezes; the bit
 *     `0x20` is unconfirmed on real hardware. "Changed" = the value changed ([TireSensor.valueSinceMs]):
 *     frozen ≥ [FROZEN_LOST_AFTER_MS] while every other road wheel (at least two) changed within
 *     [FROZEN_PEER_WINDOW_MS]. Deliberately conservative; to be tuned on hardware.
 *   - Sticky: once lost, it stays lost until the sensor itself changes (a parked car whose other
 *     sensors fall quiet does not clear it).
 * - **Low against the axle** (LOW_PRESSURE): the wheel is [AXLE_LOW_RAISE_KPA] below the other wheel
 *   of its axle (cleared below [AXLE_LOW_CLEAR_KPA]). Heat and weather move both wheels of an axle
 *   alike, so a growing gap is a puncture long before the absolute low limit.
 *
 * The spare is never checked nor used as a peer.
 */
object WheelCrossCheck {

    const val HID_LOST_AFTER_MS = 10 * 60_000L
    const val HID_PEER_WINDOW_MS = 3 * 60_000L
    const val HID_MIN_ACTIVE_PEERS = 2
    const val FROZEN_LOST_AFTER_MS = 45 * 60_000L
    const val FROZEN_PEER_WINDOW_MS = 10 * 60_000L
    const val AXLE_LOW_RAISE_KPA = 40f
    const val AXLE_LOW_CLEAR_KPA = 30f

    private val AXLE_MATES = mapOf("FL" to "FR", "FR" to "FL", "RL" to "RR", "RR" to "RL")

    /**
     * [sensor] (own alert already applied) with the cross-wheel alerts added.
     *
     * @param slot the wheel slot [sensor] sits on, null if not placed.
     * @param wheels the current wheels (slot → sensor); [sensor]'s own entry is ignored.
     * @param previous the sensor's previous reading (its alert from the last check), null if none.
     * @param streaming the session's protocol repeats frames continuously (AA55/Deelife).
     */
    fun apply(
        sensor: TireSensor,
        slot: String?,
        wheels: Map<String, TireSensor>,
        previous: TireSensor?,
        streaming: Boolean,
        now: Long
    ): TireSensor {
        if (sensor.alertType.toSeverity() == AlertSeverity.ALERT || WheelLayout.isSpare(sensor)) return sensor
        val peers = wheels.values.filter { it.id != sensor.id && !WheelLayout.isSpare(it) }
        val wasLost = previous?.alertType == AlertType.SENSOR_LOST && !previous.flags.noSignal
        if (isLost(sensor, peers, wasLost, streaming, now)) return sensor.copy(alertType = AlertType.SENSOR_LOST)
        val mate = slot?.let { AXLE_MATES[it] }?.let { wheels[it] }?.takeIf { it.id != sensor.id }
        val wasLow = previous?.alertType == AlertType.LOW_PRESSURE
        if (isLowAgainstAxle(sensor, mate, wasLow)) return sensor.copy(alertType = AlertType.LOW_PRESSURE)
        return sensor
    }

    /** When [sensor]'s value last changed: HID its last transmission, streaming its value start. */
    fun lastChangeAtMs(sensor: TireSensor, streaming: Boolean): Long =
        if (streaming) sensor.valueSinceMs.takeIf { it > 0L } ?: sensor.timestamp else sensor.timestamp

    private fun isLost(
        sensor: TireSensor,
        peers: List<TireSensor>,
        wasLost: Boolean,
        streaming: Boolean,
        now: Long
    ): Boolean {
        val lostAfter = if (streaming) FROZEN_LOST_AFTER_MS else HID_LOST_AFTER_MS
        if (now - lastChangeAtMs(sensor, streaming) < lostAfter) return false
        if (wasLost) return true
        val window = if (streaming) FROZEN_PEER_WINDOW_MS else HID_PEER_WINDOW_MS
        val active = peers.count { now - lastChangeAtMs(it, streaming) <= window }
        return if (streaming) peers.size >= 2 && active == peers.size else active >= HID_MIN_ACTIVE_PEERS
    }

    private fun isLowAgainstAxle(sensor: TireSensor, mate: TireSensor?, wasLow: Boolean): Boolean {
        if (mate == null || mate.alertType == AlertType.SENSOR_LOST) return false
        if (!sensor.pressureKpa.isFinite() || !mate.pressureKpa.isFinite()) return false
        val gap = mate.pressureKpa - sensor.pressureKpa
        return gap >= if (wasLow) AXLE_LOW_CLEAR_KPA else AXLE_LOW_RAISE_KPA
    }
}
