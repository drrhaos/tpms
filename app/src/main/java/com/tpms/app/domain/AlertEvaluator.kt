package com.tpms.app.domain

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

    /**
     * Decides the shown alert for a confirmed reading and updates [memory]. Priority: receiver flags,
     * then thresholds, then the low-battery warning, so a warning never hides an alert.
     *
     * - SENSOR_LOST: the noSignal flag, shown at once and gone with the first reading without it.
     *   Such a reading leaves [memory] untouched: its payload is not a measurement.
     * - LOW_PRESSURE from the leak flag: only after it has held for 15 s without a break.
     * - Thresholds raise at once and clear with hysteresis (10 kPa, 3 °C) to stop flapping at the
     *   limit. The spare slot skips them: without a fifth sensor the receiver fills it with a dummy.
     * - BATTERY_LOW latches; it clears only after the flag is off and the level is >= 15 % (or
     *   unknown) for 10 min straight.
     *
     * Every other reading updates each condition's memory, whichever alert ends up shown.
     */
    fun evaluate(
        sensor: TireSensor,
        thresholds: AlertThresholds,
        memory: AlertMemory,
        now: Long
    ): AlertDecision {
        // A no-signal frame carries no measurement, so it must not move any remembered state.
        if (sensor.flags.noSignal) return AlertDecision(AlertType.SENSOR_LOST, memory)

        val leakSinceMs = if (sensor.flags.leak) memory.leakSinceMs ?: now else null
        val thresholdAlert =
            if (WheelLayout.isSpare(sensor)) null else thresholdAlert(sensor, thresholds, memory.thresholdAlert)
        val battery = batteryLatch(sensor, memory, now)
        val next = AlertMemory(
            thresholdAlert = thresholdAlert,
            leakSinceMs = leakSinceMs,
            batteryLatched = battery.latched,
            batteryOkSinceMs = battery.okSinceMs
        )
        val alert = when {
            leakSinceMs != null && now - leakSinceMs >= LEAK_HOLD_MS -> AlertType.LOW_PRESSURE
            thresholdAlert != null -> thresholdAlert
            battery.latched -> AlertType.BATTERY_LOW
            else -> null
        }
        return AlertDecision(alert, next)
    }

    private fun thresholdAlert(sensor: TireSensor, thresholds: AlertThresholds, held: AlertType?): AlertType? {
        val pressure = sensor.pressureKpa
        val temperature = sensor.temperatureCelsius
        return when {
            pressure < thresholds.lowPressureKpa -> AlertType.LOW_PRESSURE
            pressure > thresholds.highPressureKpa -> AlertType.HIGH_PRESSURE
            temperature > thresholds.highTempCelsius -> AlertType.HIGH_TEMP
            else -> held?.takeIf {
                when (it) {
                    AlertType.LOW_PRESSURE -> pressure < thresholds.lowPressureKpa + PRESSURE_MARGIN_KPA
                    AlertType.HIGH_PRESSURE -> pressure > thresholds.highPressureKpa - PRESSURE_MARGIN_KPA
                    AlertType.HIGH_TEMP -> temperature > thresholds.highTempCelsius - TEMP_MARGIN_CELSIUS
                    else -> false
                }
            }
        }
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
