package com.tpms.app.data.usb

import com.tpms.app.domain.model.TireSensor

object SensorValidator {

    private const val MIN_PRESSURE_KPA = 0f
    private const val MAX_PRESSURE_KPA = 2000f
    private const val MIN_TEMP_C = -60f
    private const val MAX_TEMP_C = 150f
    /** Below this, spare-wheel slot readings are treated as "no sensor". */
    private const val MIN_LIVE_WHEEL_PRESSURE_KPA = 100f

    fun sanitize(sensor: TireSensor): TireSensor? {
        if (sensor.id.isBlank()) return null
        if (!sensor.pressureKpa.isFinite() || !sensor.temperatureCelsius.isFinite()) return null
        if (sensor.pressureKpa !in MIN_PRESSURE_KPA..MAX_PRESSURE_KPA) return null
        if (sensor.temperatureCelsius !in MIN_TEMP_C..MAX_TEMP_C) return null
        // Spare slot often reports garbage when no TPMS sensor is installed
        if (sensor.id == "SP" && sensor.pressureKpa < MIN_LIVE_WHEEL_PRESSURE_KPA) return null
        val battery = sensor.batteryPercent.coerceIn(0, 100)
        return if (battery == sensor.batteryPercent) sensor else sensor.copy(batteryPercent = battery)
    }
}
