package com.tpms.app.ui.main

import com.tpms.app.domain.model.TireSensor
import com.tpms.app.domain.model.TpmsState

object MainUiHealth {

    fun firstMissingWheelLabel(uiState: MainUiState): String? {
        val live = uiState.tpmsState is TpmsState.Connected || uiState.tpmsState is TpmsState.Alert
        if (!live) return null
        val index = uiState.wheelSlotLabels.indices.firstOrNull { i ->
            uiState.wheelSlotLabels[i] != "SP" && uiState.wheelSlots.getOrNull(i) == null
        } ?: return null
        return uiState.wheelSlotLabels[index]
    }

    /** Slot the repository placed [sensor] on ([MainUiState.wheelSlots]), else its label or id. */
    fun wheelLabelOf(uiState: MainUiState, sensor: TireSensor): String =
        uiState.wheelSlots.indexOfFirst { it?.id == sensor.id }
            .takeIf { it >= 0 }
            ?.let { uiState.wheelSlotLabels.getOrNull(it) }
            ?: sensor.label.ifEmpty { sensor.id }
}
