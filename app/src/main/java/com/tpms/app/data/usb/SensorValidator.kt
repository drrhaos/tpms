package com.tpms.app.data.usb

import com.tpms.app.data.settings.SensorValidatorDefaults
import com.tpms.app.domain.model.TireSensor

object SensorValidator {

    private const val MIN_PRESSURE_KPA = 0f
    private const val MAX_PRESSURE_KPA = 2000f
    private const val MIN_TEMP_C = -60f
    private const val MAX_TEMP_C = 150f

    fun sanitize(
        sensor: TireSensor,
        minLiveWheelPressureKpa: Float = SensorValidatorDefaults.MIN_LIVE_WHEEL_PRESSURE_KPA
    ): TireSensor? {
        if (sensor.id.isBlank()) return null
        if (!sensor.pressureKpa.isFinite() || !sensor.temperatureCelsius.isFinite()) return null
        if (sensor.pressureKpa !in MIN_PRESSURE_KPA..MAX_PRESSURE_KPA) return null
        if (sensor.temperatureCelsius !in MIN_TEMP_C..MAX_TEMP_C) return null
        if (sensor.id == "SP" && sensor.pressureKpa < minLiveWheelPressureKpa) return null
        val battery = sensor.batteryPercent.coerceIn(0, 100)
        return if (battery == sensor.batteryPercent) sensor else sensor.copy(batteryPercent = battery)
    }
}
