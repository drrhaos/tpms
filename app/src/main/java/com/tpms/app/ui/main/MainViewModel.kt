package com.tpms.app.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tpms.app.data.diagnostics.UiBreadcrumbs
import com.tpms.app.data.repository.TpmsRepository
import com.tpms.app.data.settings.SettingsStore
import com.tpms.app.data.usb.UsbDebugLog
import com.tpms.app.domain.WheelLayout
import com.tpms.app.domain.model.PressureUnit
import com.tpms.app.domain.model.TireSensor
import com.tpms.app.domain.model.TpmsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class MainUiState(
    val tpmsState: TpmsState = TpmsState.Disconnected,
    val sensors: Map<String, TireSensor> = emptyMap(),
    val wheelSlots: List<TireSensor?> = List(WheelLayout.ORDER.size) { null },
    val dashboardSensors: List<TireSensor> = emptyList(),
    val pressureUnit: PressureUnit = PressureUnit.KPA,
    val lastError: String? = null
)

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    repository: TpmsRepository,
    settingsStore: SettingsStore,
    private val debugLog: UsbDebugLog,
    private val uiBreadcrumbs: UiBreadcrumbs
) : AndroidViewModel(application) {

    val uiState: StateFlow<MainUiState> = combine(
        repository.state,
        repository.sensors,
        settingsStore.pressureUnit
    ) { tpmsState, sensors, unit ->
        buildUiState(tpmsState, sensors, unit)
    }.catch { error ->
        debugLog.error("MainScreen", uiBreadcrumbs.describe())
        debugLog.exception("MainScreen", error, "ui state flow")
        emit(MainUiState(lastError = error.message ?: error.javaClass.simpleName))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainUiState())

    private fun buildUiState(
        tpmsState: TpmsState,
        sensors: Map<String, TireSensor>,
        unit: PressureUnit
    ): MainUiState {
        return try {
            MainUiState(
                tpmsState = tpmsState,
                sensors = sensors,
                wheelSlots = WheelLayout.orderedSlots(sensors),
                dashboardSensors = WheelLayout.orderedValues(sensors),
                pressureUnit = unit
            )
        } catch (error: Exception) {
            debugLog.error("MainScreen", uiBreadcrumbs.describe())
            debugLog.exception("MainScreen", error, "buildUiState")
            MainUiState(
                tpmsState = tpmsState,
                sensors = sensors,
                pressureUnit = unit,
                lastError = error.message ?: error.javaClass.simpleName
            )
        }
    }

    fun logUiError(component: String, error: Throwable) {
        debugLog.error("MainScreen/$component", uiBreadcrumbs.describe())
        debugLog.exception("MainScreen/$component", error)
    }
}
