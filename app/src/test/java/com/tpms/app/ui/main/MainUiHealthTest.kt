package com.tpms.app.ui.main

import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.TireSensor
import com.tpms.app.domain.model.TpmsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainUiHealthTest {

    @Test
    fun hasMalfunction_trueWhenSensorLost() {
        val sensor = sampleSensor(alertType = AlertType.SENSOR_LOST)
        val state = MainUiState(
            tpmsState = TpmsState.Alert(sensor, AlertType.SENSOR_LOST, null),
            wheelSlots = listOf(sensor, null, null, null)
        )
        assertTrue(MainUiHealth.hasMalfunction(state))
    }

    @Test
    fun hasMalfunction_trueWhenWheelMissingWhileConnected() {
        val state = MainUiState(
            tpmsState = TpmsState.Connected(listOf(sampleSensor()), System.currentTimeMillis()),
            wheelSlots = listOf(sampleSensor(), null, null, null)
        )
        assertTrue(MainUiHealth.hasMalfunction(state))
    }

    @Test
    fun hasMalfunction_falseWhenAllHealthy() {
        val sensor = sampleSensor()
        val state = MainUiState(
            tpmsState = TpmsState.Connected(listOf(sensor), System.currentTimeMillis()),
            wheelSlots = listOf(sensor, sensor, sensor, sensor)
        )
        assertFalse(MainUiHealth.hasMalfunction(state))
    }

    @Test
    fun firstMissingWheelLabel_returnsFirstEmptySlot() {
        val state = MainUiState(
            tpmsState = TpmsState.Connected(emptyList(), System.currentTimeMillis()),
            wheelSlots = listOf(sampleSensor(), null, null, null),
            wheelSlotLabels = listOf("FL", "FR", "RL", "RR")
        )
        assertEquals("FR", MainUiHealth.firstMissingWheelLabel(state))
    }

    @Test
    fun firstMissingWheelLabel_nullWhenDisconnected() {
        val state = MainUiState(
            tpmsState = TpmsState.Disconnected,
            wheelSlots = listOf(null, null, null, null)
        )
        assertNull(MainUiHealth.firstMissingWheelLabel(state))
    }

    private fun sampleSensor(alertType: AlertType? = null): TireSensor =
        TireSensor(
            id = "1",
            label = "FL",
            pressureKpa = 220f,
            temperatureCelsius = 25f,
            batteryPercent = 90,
            alertType = alertType,
            timestamp = System.currentTimeMillis()
        )
}
