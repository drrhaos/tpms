package com.tpms.app.domain

import com.tpms.app.domain.model.AlertLevel
import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.TireSensor

/** UI colour of a wheel; derived from the alert level (ATTENTION = WARNING, FAULT = ALERT). */
enum class AlertSeverity {
    OK, WARNING, ALERT, DISCONNECTED
}

fun AlertLevel?.toSeverity(): AlertSeverity = when (this) {
    AlertLevel.FAULT -> AlertSeverity.ALERT
    AlertLevel.ATTENTION -> AlertSeverity.WARNING
    null -> AlertSeverity.OK
}

fun TireSensor?.toSeverity(): AlertSeverity = when {
    this == null -> AlertSeverity.DISCONNECTED
    alertType == null -> AlertSeverity.OK
    else -> alertLevel.toSeverity()
}

/**
 * Orders (type, level) pairs: the higher level first, then within a level
 * SENSOR_LOST > PRESSURE_DROP > LOW_PRESSURE > HIGH_PRESSURE > HIGH_TEMP > BATTERY_LOW
 * (wayfinder alerts 01). `maxWith(AlertRank)` picks the one to show.
 */
object AlertRank : Comparator<Pair<AlertType, AlertLevel>> {
    private val ORDER = listOf(
        AlertType.BATTERY_LOW,
        AlertType.HIGH_TEMP,
        AlertType.HIGH_PRESSURE,
        AlertType.LOW_PRESSURE,
        AlertType.PRESSURE_DROP,
        AlertType.SENSOR_LOST
    )

    override fun compare(a: Pair<AlertType, AlertLevel>, b: Pair<AlertType, AlertLevel>): Int {
        val byLevel = a.second.compareTo(b.second)
        return if (byLevel != 0) byLevel else ORDER.indexOf(a.first).compareTo(ORDER.indexOf(b.first))
    }
}
