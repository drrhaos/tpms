package com.tpms.app.domain.model

sealed interface TpmsState {
    data object Disconnected : TpmsState
    data class Connecting(val attempt: Int) : TpmsState
    data class Connected(
        val sensors: List<TireSensor>,
        val timestamp: Long
    ) : TpmsState

    data class Alert(
        val sensor: TireSensor,
        val type: AlertType,
        val previousState: Connected?
    ) : TpmsState
}
