package com.tpms.app.domain

import com.tpms.app.domain.model.AlertLevel
import com.tpms.app.domain.model.AlertThresholds
import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.TireSensor

object AlertEvaluator {
    private const val LOW_BATTERY_PERCENT = 10
    private const val BATTERY_OK_PERCENT = 15
    private const val BATTERY_OK_HOLD_MS = 10 * 60_000L
    private const val LEAK_HOLD_MS = 15_000L
    private const val PRESSURE_MARGIN_KPA = 10f
    private const val TEMP_MARGIN_CELSIUS = 3f

    /** Fast loss: a fall of at least [DROP_KPA] against any pressure of the last [DROP_WINDOW_MS]. */
    const val DROP_KPA = 30f
    const val DROP_WINDOW_MS = 10 * 60_000L
    const val SAMPLE_EVERY_MS = 30_000L

    /**
     * Decides the shown alert and its level for a confirmed reading and updates [memory]
     * (wayfinder alerts 01).
     *
     * FAULT ("Неисправность"):
     * - SENSOR_LOST: the noSignal flag, at once; gone with the first reading without it. Such a
     *   reading leaves [memory] untouched: its payload is not a measurement.
     * - PRESSURE_DROP: the leak flag held 15 s without a break, or a fall of [DROP_KPA] within
     *   [DROP_WINDOW_MS] (clears once the pressure held for that window).
     * - LOW_PRESSURE below the critical limit (cleared 10 kPa above it, then ATTENTION).
     * - HIGH_TEMP at or over the critical limit (cleared 3 °C below it).
     *
     * ATTENTION: LOW_PRESSURE / HIGH_PRESSURE past the limits and HIGH_TEMP (hysteresis 10 kPa /
     * 3 °C), BATTERY_LOW (latched; clears only after the flag is off and the level is >= 15 % or
     * unknown for 10 min straight).
     *
     * The spare skips the thresholds and the rate: without a fifth sensor the receiver fills it
     * with a dummy. Several conditions: the highest level wins, then [AlertRank]. Every reading
     * updates each condition's memory, whichever alert ends up shown.
     */
    fun evaluate(
        sensor: TireSensor,
        thresholds: AlertThresholds,
        memory: AlertMemory,
        now: Long
    ): AlertDecision {
        // A no-signal frame carries no measurement, so it must not move any remembered state.
        if (sensor.flags.noSignal) return AlertDecision(AlertType.SENSOR_LOST, AlertLevel.FAULT, memory)

        val spare = WheelLayout.isSpare(sensor)
        val leakSinceMs = if (sensor.flags.leak) memory.leakSinceMs ?: now else null
        val pressure = if (spare) null else pressureAlert(sensor.pressureKpa, thresholds, memory)
        val temp = if (spare) null else tempAlert(sensor.temperatureCelsius, thresholds, memory)
        val samples = if (spare) emptyList() else recordSample(memory.pressureSamples, sensor.pressureKpa, now)
        val drop = if (spare) null else recentDrop(samples, sensor.pressureKpa, now)
        val dropping = drop != null && drop >= DROP_KPA
        val battery = batteryLatch(sensor, memory, now)
        val next = AlertMemory(
            pressureAlert = pressure?.first,
            pressureFault = pressure?.second == AlertLevel.FAULT,
            tempAlert = temp != null,
            tempFault = temp == AlertLevel.FAULT,
            leakSinceMs = leakSinceMs,
            batteryLatched = battery.latched,
            batteryOkSinceMs = battery.okSinceMs,
            pressureSamples = samples
        )
        val candidates = buildList {
            if (leakSinceMs != null && now - leakSinceMs >= LEAK_HOLD_MS) add(AlertType.PRESSURE_DROP to AlertLevel.FAULT)
            if (dropping) add(AlertType.PRESSURE_DROP to AlertLevel.FAULT)
            pressure?.let { add(it) }
            temp?.let { add(AlertType.HIGH_TEMP to it) }
            if (battery.latched) add(AlertType.BATTERY_LOW to AlertLevel.ATTENTION)
        }
        val chosen = candidates.maxWithOrNull(AlertRank)
        return AlertDecision(chosen?.first, chosen?.second, next, drop)
    }

    /** LOW / HIGH pressure with its level, hysteresis on clear for both the alert and the FAULT. */
    private fun pressureAlert(kpa: Float, t: AlertThresholds, memory: AlertMemory): Pair<AlertType, AlertLevel>? {
        val heldLowFault = memory.pressureAlert == AlertType.LOW_PRESSURE && memory.pressureFault
        val critical = kpa < t.criticalLowPressureKpa ||
            (heldLowFault && kpa < t.criticalLowPressureKpa + PRESSURE_MARGIN_KPA)
        val type = when {
            critical || kpa < t.lowPressureKpa -> AlertType.LOW_PRESSURE
            kpa > t.highPressureKpa -> AlertType.HIGH_PRESSURE
            memory.pressureAlert == AlertType.LOW_PRESSURE && kpa < t.lowPressureKpa + PRESSURE_MARGIN_KPA -> AlertType.LOW_PRESSURE
            memory.pressureAlert == AlertType.HIGH_PRESSURE && kpa > t.highPressureKpa - PRESSURE_MARGIN_KPA -> AlertType.HIGH_PRESSURE
            else -> return null
        }
        return type to if (type == AlertType.LOW_PRESSURE && critical) AlertLevel.FAULT else AlertLevel.ATTENTION
    }

    /** HIGH_TEMP level, or null; hysteresis on clear for both the alert and the FAULT. */
    private fun tempAlert(celsius: Float, t: AlertThresholds, memory: AlertMemory): AlertLevel? {
        val critical = celsius >= t.criticalTempCelsius ||
            (memory.tempFault && celsius > t.criticalTempCelsius - TEMP_MARGIN_CELSIUS)
        return when {
            critical -> AlertLevel.FAULT
            celsius > t.highTempCelsius -> AlertLevel.ATTENTION
            memory.tempAlert && celsius > t.highTempCelsius - TEMP_MARGIN_CELSIUS -> AlertLevel.ATTENTION
            else -> null
        }
    }

    /** Keeps the samples of the last window; adds [kpa] when the newest is [SAMPLE_EVERY_MS] old. */
    private fun recordSample(samples: List<PressureSample>, kpa: Float, now: Long): List<PressureSample> {
        if (!kpa.isFinite()) return samples
        // A clock that went back drops the history rather than mixing two timelines.
        val kept = samples.filter { it.atMs in (now - DROP_WINDOW_MS)..now }
        val newest = kept.lastOrNull()
        return if (newest == null || now - newest.atMs >= SAMPLE_EVERY_MS) kept + PressureSample(now, kpa) else kept
    }

    /** Fall of [kpa] against the highest sample of the window; null when it did not fall. */
    private fun recentDrop(samples: List<PressureSample>, kpa: Float, now: Long): Float? {
        if (!kpa.isFinite()) return null
        val highest = samples.filter { now - it.atMs <= DROP_WINDOW_MS }.maxOfOrNull { it.kpa } ?: return null
        return (highest - kpa).takeIf { it > 0f }
    }

    private data class BatteryLatch(val latched: Boolean, val okSinceMs: Long? = null)

    private fun batteryLatch(sensor: TireSensor, memory: AlertMemory, now: Long): BatteryLatch {
        val percent = sensor.batteryPercent
        val lowNow = sensor.flags.lowBattery || (percent != null && percent < LOW_BATTERY_PERCENT)
        if (lowNow) return BatteryLatch(latched = true)
        if (!memory.batteryLatched) return BatteryLatch(latched = false)
        // Not low-now, so the flag is off; 10..14 % is neither low nor healthy and restarts the clock.
        val okNow = percent == null || percent >= BATTERY_OK_PERCENT
        if (!okNow) return BatteryLatch(latched = true)
        val okSince = memory.batteryOkSinceMs ?: now
        return if (now - okSince >= BATTERY_OK_HOLD_MS) {
            BatteryLatch(latched = false)
        } else {
            BatteryLatch(latched = true, okSinceMs = okSince)
        }
    }
}
