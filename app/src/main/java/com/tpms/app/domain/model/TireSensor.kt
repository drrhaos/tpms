package com.tpms.app.domain.model

data class TireSensor(
    val id: String,
    val label: String = "",
    val pressureKpa: Float,
    val temperatureCelsius: Float,
    /** Battery level when the protocol reports one; null when it only reports [SensorFlags.lowBattery]. */
    val batteryPercent: Int?,
    val alertType: AlertType?,
    val timestamp: Long,
    val flags: SensorFlags = SensorFlags.NONE
) {
    val isAlert: Boolean get() = alertType != null
    val isLowBattery: Boolean get() = flags.lowBattery || alertType == AlertType.BATTERY_LOW
}

/** Hardware status reported by the receiver itself, independent of alert thresholds. */
data class SensorFlags(
    val noSignal: Boolean = false,
    val leak: Boolean = false,
    val lowBattery: Boolean = false
) {
    companion object {
        val NONE = SensorFlags()
    }
}

enum class AlertType {
    LOW_PRESSURE, HIGH_PRESSURE, HIGH_TEMP, BATTERY_LOW, SENSOR_LOST
}
