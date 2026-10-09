package com.tpms.app.domain

import com.tpms.app.domain.model.AlertThresholds
import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.TireSensor

object AlertEvaluator {
    private const val LOW_BATTERY_PERCENT = 10

    /**
     * Receiver flags first, then thresholds, then the low-battery warning, so a warning never hides
     * an alert. The spare slot skips thresholds: without a fifth sensor the receiver fills it with
     * a fixed dummy reading.
     */
    fun evaluate(sensor: TireSensor, thresholds: AlertThresholds): AlertType? = when {
        sensor.flags.noSignal -> AlertType.SENSOR_LOST
        sensor.flags.leak -> AlertType.LOW_PRESSURE
        else -> (if (WheelLayout.isSpare(sensor)) null else thresholdAlert(sensor, thresholds))
            ?: AlertType.BATTERY_LOW.takeIf { isBatteryLow(sensor) }
    }

    private fun thresholdAlert(sensor: TireSensor, thresholds: AlertThresholds): AlertType? = when {
        sensor.pressureKpa < thresholds.lowPressureKpa -> AlertType.LOW_PRESSURE
        sensor.pressureKpa > thresholds.highPressureKpa -> AlertType.HIGH_PRESSURE
        sensor.temperatureCelsius > thresholds.highTempCelsius -> AlertType.HIGH_TEMP
        else -> null
    }

    private fun isBatteryLow(sensor: TireSensor): Boolean {
        val battery = sensor.batteryPercent
        return sensor.flags.lowBattery || (battery != null && battery < LOW_BATTERY_PERCENT)
    }
}
