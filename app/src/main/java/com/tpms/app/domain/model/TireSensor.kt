package com.tpms.app.domain.model

data class TireSensor(
    val id: String,
    val label: String = "",
    val pressureKpa: Float,
    val temperatureCelsius: Float,
    val batteryPercent: Int,
    val alertType: AlertType?,
    val timestamp: Long
) {
    val isAlert: Boolean get() = alertType != null
}

enum class AlertType {
    LOW_PRESSURE, HIGH_PRESSURE, HIGH_TEMP, BATTERY_LOW, SENSOR_LOST
}
