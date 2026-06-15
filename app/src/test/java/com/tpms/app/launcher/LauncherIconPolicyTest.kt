package com.tpms.app.launcher

import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.TireSensor
import com.tpms.app.domain.model.TpmsState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherIconPolicyTest {

    @Test
    fun isHealthy_trueWhenConnectedWithoutAlerts() {
        val sensors = listOf(
            sensor("1", null),
            sensor("2", null)
        )
        assertTrue(LauncherIconPolicy.isHealthy(TpmsState.Connected(sensors, 1L), sensors))
    }

    @Test
    fun isHealthy_falseWhenAlertState() {
        val sensors = listOf(sensor("1", null))
        val state = TpmsState.Alert(sensor("1", AlertType.LOW_PRESSURE), AlertType.LOW_PRESSURE, null)
        assertFalse(LauncherIconPolicy.isHealthy(state, sensors))
    }

    @Test
    fun isHealthy_falseWhenDisconnected() {
        assertFalse(LauncherIconPolicy.isHealthy(TpmsState.Disconnected, emptyList()))
    }

    @Test
    fun isHealthy_falseWhenSensorHasAlert() {
        val sensors = listOf(sensor("1", AlertType.HIGH_TEMP))
        assertFalse(
            LauncherIconPolicy.isHealthy(TpmsState.Connected(sensors, 1L), sensors)
        )
    }

    private fun sensor(id: String, alert: AlertType?): TireSensor =
        TireSensor(
            id = id,
            label = id,
            pressureKpa = 220f,
            temperatureCelsius = 25f,
            batteryPercent = 90,
            alertType = alert,
            timestamp = 1L
        )
}
