package com.tpms.app.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tpms.app.data.diagnostics.UiBreadcrumbs
import com.tpms.app.data.repository.TpmsRepository
import com.tpms.app.data.settings.SettingsStore
import com.tpms.app.data.usb.UsbDebugLog
import com.tpms.app.domain.MonitoringHealthPolicy
import com.tpms.app.domain.SensorFreshness
import com.tpms.app.domain.UsbFault
import com.tpms.app.domain.WheelLayout
import com.tpms.app.domain.model.PressureUnit
import com.tpms.app.domain.model.SettingsUiMode
import com.tpms.app.domain.model.TireSensor
import com.tpms.app.domain.model.TpmsState
import com.tpms.app.service.TpmsMonitorService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val tpmsState: TpmsState = TpmsState.Disconnected,
    val sensors: Map<String, TireSensor> = emptyMap(),
    val wheelSlots: List<TireSensor?> = List(WheelLayout.ORDER.size) { null },
    val wheelSlotLabels: List<String> = WheelLayout.ORDER,
    /** Per slot (parallel to [wheelSlots]): minutes for the "N min" caption of a stale wheel, else null. */
    val wheelStaleMinutes: List<Long?> = emptyList(),
    /** Per slot: stale wheel drawn muted (an active alert keeps its colour instead). */
    val wheelMuted: List<Boolean> = emptyList(),
    val wheelMapping: Map<String, String> = emptyMap(),
    val pressureUnit: PressureUnit = PressureUnit.KPA,
    val advancedMode: Boolean = false,
    val debugToolsEnabled: Boolean = false,
    val lastError: String? = null,
    val dataStale: Boolean = false,
    val dataAgeMinutes: Long? = null,
    val protocolUnhealthy: Boolean = false,
    val monitoringOffline: Boolean = false,
    val usbFault: UsbFault? = null
)

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val repository: TpmsRepository,
    settingsStore: SettingsStore,
    private val debugLog: UsbDebugLog,
    private val uiBreadcrumbs: UiBreadcrumbs
) : AndroidViewModel(application) {

    /** Re-evaluates time-based staleness (per-wheel and overall) while nothing else changes. */
    private val freshnessTicker = flow {
        while (true) {
            emit(Unit)
            delay(FRESHNESS_TICK_MS)
        }
    }

    val uiState: StateFlow<MainUiState> = combine(
        combine(repository.state, freshnessTicker) { state, _ -> state },
        repository.sensors,
        repository.usbFault,
        combine(
            settingsStore.pressureUnit,
            settingsStore.wheelMapping,
            settingsStore.showSpareWheel,
            settingsStore.settingsUiMode,
            settingsStore.debugToolsEnabled
        ) { unit, mapping, spare, uiMode, debugTools ->
            MainSettingsBundle(
                unit,
                mapping,
                spare,
                uiMode == SettingsUiMode.ADVANCED,
                debugTools
            )
        }
    ) { tpmsState, sensors, usbFault, settings ->
        buildUiState(tpmsState, sensors, usbFault, settings)
    }.catch { error ->
        debugLog.error("MainScreen", uiBreadcrumbs.describe())
        debugLog.exception("MainScreen", error, "ui state flow")
        emit(MainUiState(lastError = error.message ?: error.javaClass.simpleName))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainUiState())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun checkNow() {
        viewModelScope.launch {
            _isRefreshing.value = true
            TpmsMonitorService.wake(getApplication())
            delay(CHECK_NOW_REFRESH_MS)
            _isRefreshing.value = false
        }
    }

    private fun buildUiState(
        tpmsState: TpmsState,
        sensors: Map<String, TireSensor>,
        usbFault: UsbFault?,
        settings: MainSettingsBundle
    ): MainUiState {
        return try {
            val slots = WheelLayout.allSlots(settings.showSpareWheel)
            val wheelSlots = WheelLayout.orderedSlots(sensors, settings.wheelMapping, settings.showSpareWheel)
            val now = System.currentTimeMillis()
            val ageSec = repository.dataAgeSec(now)
            val stale = repository.isDataStale(now)
            val wheelProtocol = repository.wheelFreshnessProtocol()
            val protocolUnhealthy = repository.isProtocolUnhealthy()
            val monitoringOffline = MonitoringHealthPolicy.shouldAlertMonitoringOffline(
                repository.disconnectedSinceMs()
            )
            MainUiState(
                tpmsState = tpmsState,
                sensors = sensors,
                wheelSlots = wheelSlots,
                wheelSlotLabels = slots,
                wheelStaleMinutes = wheelSlots.map { SensorFreshness.staleWheelMinutes(it, wheelProtocol, now) },
                wheelMuted = wheelSlots.map { SensorFreshness.isWheelMuted(it, wheelProtocol, now) },
                wheelMapping = settings.wheelMapping,
                pressureUnit = settings.pressureUnit,
                advancedMode = settings.advancedMode,
                debugToolsEnabled = settings.debugToolsEnabled,
                dataStale = stale,
                dataAgeMinutes = ageSec?.let { (it / 60).coerceAtLeast(1) },
                protocolUnhealthy = protocolUnhealthy,
                monitoringOffline = monitoringOffline,
                usbFault = usbFault
            )
        } catch (error: Exception) {
            debugLog.error("MainScreen", uiBreadcrumbs.describe())
            debugLog.exception("MainScreen", error, "buildUiState")
            MainUiState(
                tpmsState = tpmsState,
                sensors = sensors,
                pressureUnit = settings.pressureUnit,
                lastError = error.message ?: error.javaClass.simpleName
            )
        }
    }

    fun logUiError(component: String, error: Throwable) {
        debugLog.error("MainScreen/$component", uiBreadcrumbs.describe())
        debugLog.exception("MainScreen/$component", error)
    }

    companion object {
        private const val CHECK_NOW_REFRESH_MS = 1500L
        private const val FRESHNESS_TICK_MS = 30_000L
    }
}

private data class MainSettingsBundle(
    val pressureUnit: PressureUnit,
    val wheelMapping: Map<String, String>,
    val showSpareWheel: Boolean,
    val advancedMode: Boolean,
    val debugToolsEnabled: Boolean
)
