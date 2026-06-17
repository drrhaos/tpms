package com.tpms.app.ui.main

import com.tpms.app.domain.model.TpmsState

object MainUiHealth {

    fun hasMalfunction(uiState: MainUiState): Boolean {
        if (uiState.lastError != null) return true
        if (uiState.dataStale || uiState.protocolUnhealthy) return true
        when (uiState.tpmsState) {
            is TpmsState.Disconnected,
            is TpmsState.Alert -> return true
            is TpmsState.Connecting,
            is TpmsState.Connected -> Unit
        }
        return uiState.wheelSlots.indices.any { index ->
            val label = uiState.wheelSlotLabels.getOrNull(index) ?: return@any false
            if (label == "SP") return@any false
            val sensor = uiState.wheelSlots[index]
            sensor == null || sensor.isAlert
        }
    }

    fun firstMissingWheelLabel(uiState: MainUiState): String? {
        val live = uiState.tpmsState is TpmsState.Connected || uiState.tpmsState is TpmsState.Alert
        if (!live) return null
        val index = uiState.wheelSlotLabels.indices.firstOrNull { i ->
            uiState.wheelSlotLabels[i] != "SP" && uiState.wheelSlots.getOrNull(i) == null
        } ?: return null
        return uiState.wheelSlotLabels[index]
    }
}
