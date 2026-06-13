package com.tpms.app.domain

import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.TireSensor

enum class AlertSeverity {
    OK, WARNING, ALERT, DISCONNECTED
}

fun AlertType?.toSeverity(): AlertSeverity = when (this) {
    AlertType.LOW_PRESSURE,
    AlertType.HIGH_PRESSURE,
    AlertType.SENSOR_LOST -> AlertSeverity.ALERT
    AlertType.HIGH_TEMP,
    AlertType.BATTERY_LOW -> AlertSeverity.WARNING
    null -> AlertSeverity.OK
}

fun TireSensor?.toSeverity(): AlertSeverity = when {
    this == null -> AlertSeverity.DISCONNECTED
    else -> alertType.toSeverity()
}
