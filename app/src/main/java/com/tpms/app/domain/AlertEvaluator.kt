package com.tpms.app.domain

import com.tpms.app.domain.model.AlertThresholds
import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.TireSensor

object AlertEvaluator {
    fun evaluate(sensor: TireSensor, thresholds: AlertThresholds): AlertType? = when {
        sensor.pressureKpa < thresholds.lowPressureKpa -> AlertType.LOW_PRESSURE
        sensor.pressureKpa > thresholds.highPressureKpa -> AlertType.HIGH_PRESSURE
        sensor.temperatureCelsius > thresholds.highTempCelsius -> AlertType.HIGH_TEMP
        sensor.batteryPercent < 10 -> AlertType.BATTERY_LOW
        else -> null
    }
}
