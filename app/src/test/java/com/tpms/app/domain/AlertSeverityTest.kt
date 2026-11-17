package com.tpms.app.domain

import com.tpms.app.domain.model.AlertLevel
import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.TireSensor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertSeverityTest {

    private fun sensor(alertType: AlertType?, level: AlertLevel?) = TireSensor(
        id = "FL",
        pressureKpa = 240f,
        temperatureCelsius = 20f,
        batteryPercent = 80,
        alertType = alertType,
        timestamp = 1L,
        alertLevel = level
    )

    @Test
    fun colourFollowsTheLevel() {
        assertEquals(AlertSeverity.ALERT, sensor(AlertType.HIGH_TEMP, AlertLevel.FAULT).toSeverity())
        assertEquals(AlertSeverity.WARNING, sensor(AlertType.LOW_PRESSURE, AlertLevel.ATTENTION).toSeverity())
        assertEquals(AlertSeverity.OK, sensor(null, null).toSeverity())
    }

    @Test
    fun nullSensorIsDisconnected() {
        val disconnected: TireSensor? = null
        assertEquals(AlertSeverity.DISCONNECTED, disconnected.toSeverity())
    }

    @Test
    fun rankPutsLevelFirstThenType() {
        assertTrue(AlertRank.compare(AlertType.BATTERY_LOW to AlertLevel.FAULT, AlertType.SENSOR_LOST to AlertLevel.ATTENTION) > 0)
        assertTrue(AlertRank.compare(AlertType.SENSOR_LOST to AlertLevel.FAULT, AlertType.PRESSURE_DROP to AlertLevel.FAULT) > 0)
        assertTrue(AlertRank.compare(AlertType.PRESSURE_DROP to AlertLevel.FAULT, AlertType.LOW_PRESSURE to AlertLevel.FAULT) > 0)
        assertTrue(AlertRank.compare(AlertType.HIGH_TEMP to AlertLevel.ATTENTION, AlertType.BATTERY_LOW to AlertLevel.ATTENTION) > 0)
    }
}
