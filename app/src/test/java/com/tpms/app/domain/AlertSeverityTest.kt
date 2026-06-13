package com.tpms.app.domain

import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.TireSensor
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertSeverityTest {

    private fun sensor(alertType: AlertType?) = TireSensor(
        id = "FL",
        pressureKpa = 240f,
        temperatureCelsius = 20f,
        batteryPercent = 80,
        alertType = alertType,
        timestamp = 1L
    )

    @Test
    fun toSeverity_mapsPressureAlertsToAlert() {
        assertEquals(AlertSeverity.ALERT, AlertType.LOW_PRESSURE.toSeverity())
        assertEquals(AlertSeverity.ALERT, AlertType.HIGH_PRESSURE.toSeverity())
    }

    @Test
    fun toSeverity_mapsSensorLostToAlert() {
        assertEquals(AlertSeverity.ALERT, AlertType.SENSOR_LOST.toSeverity())
    }

    @Test
    fun toSeverity_mapsTempAndBatteryToWarning() {
        assertEquals(AlertSeverity.WARNING, AlertType.HIGH_TEMP.toSeverity())
        assertEquals(AlertSeverity.WARNING, AlertType.BATTERY_LOW.toSeverity())
    }

    @Test
    fun toSeverity_nullSensorIsDisconnected() {
        val disconnected: TireSensor? = null
        assertEquals(AlertSeverity.DISCONNECTED, disconnected.toSeverity())
    }
}
