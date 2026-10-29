package com.tpms.app.ui.main

import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.TireSensor
import com.tpms.app.domain.model.TpmsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainUiHealthTest {

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

    @Test
    fun wheelLabelOf_usesSlotThePublishedWheelsPutTheSensorOn() {
        val mapped = sampleSensor().copy(id = "ABC123", label = "")
        val state = MainUiState(wheelSlots = listOf(null, null, mapped, null))

        assertEquals("RL", MainUiHealth.wheelLabelOf(state, mapped))
    }

    @Test
    fun wheelLabelOf_fallsBackToLabelThenIdWhenNotOnAWheel() {
        val state = MainUiState(wheelSlots = listOf(null, null, null, null))

        assertEquals("FL", MainUiHealth.wheelLabelOf(state, sampleSensor()))
        assertEquals("SENSOR_0A", MainUiHealth.wheelLabelOf(state, sampleSensor().copy(id = "SENSOR_0A", label = "")))
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
