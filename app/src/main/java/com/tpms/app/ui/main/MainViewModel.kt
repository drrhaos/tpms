package com.tpms.app.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tpms.app.data.diagnostics.UiBreadcrumbs
import com.tpms.app.data.repository.TpmsRepository
import com.tpms.app.data.settings.SettingsStore
import com.tpms.app.data.usb.UsbDebugLog
import com.tpms.app.domain.MonitoringHealthPolicy
import com.tpms.app.domain.PressureTarget
import com.tpms.app.domain.SensorFreshness
import com.tpms.app.domain.UsbFault
import com.tpms.app.domain.WheelLayout
import com.tpms.app.domain.model.PressureUnit
import com.tpms.app.domain.model.SettingsUiMode
import com.tpms.app.domain.model.TireSensor
import com.tpms.app.domain.model.TpmsState
import com.tpms.app.service.TpmsMonitorService
import com.tpms.app.service.UsbPermissionRequester
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
    /** Per slot of [wheelSlotLabels]: the sensor the repository placed there ([TpmsRepository.wheels]), or null. */
    val wheelSlots: List<TireSensor?> = List(WheelLayout.ORDER.size) { null },
    val wheelSlotLabels: List<String> = WheelLayout.ORDER,
    /** Per slot (parallel to [wheelSlots]): minutes for the "N min" caption of a stale wheel, else null. */
    val wheelStaleMinutes: List<Long?> = emptyList(),
    /** Per slot: stale wheel drawn muted (an active alert keeps its colour instead). */
    val wheelMuted: List<Boolean> = emptyList(),
    val pressureUnit: PressureUnit = PressureUnit.KPA,
    /** Recommended pressure, shown as the reference under the car. */
    val targetPressureKpa: Float = PressureTarget.DEFAULT_KPA,
    /** Service menu unlocked: technical details (sensor id, update time) and the debug shortcut. */
    val advancedMode: Boolean = false,
    val debugToolsEnabled: Boolean = false,
    val lastError: String? = null,
    /** Connection-based "data outdated" (wayfinder 10); false when there are no readings at all. */
    val dataStale: Boolean = false,
    /** Minutes since the shown data was last confirmed live; null when unknown (no minutes shown). */
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
    private val uiBreadcrumbs: UiBreadcrumbs,
    private val usbPermissionRequester: UsbPermissionRequester
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
        repository.wheels,
        repository.usbFault,
        combine(
            settingsStore.pressureUnit,
            settingsStore.showSpareWheel,
            settingsStore.settingsUiMode,
            settingsStore.debugToolsEnabled,
            settingsStore.targetPressureKpa
        ) { unit, spare, uiMode, debugTools, target ->
            MainSettingsBundle(
                unit,
                spare,
                uiMode == SettingsUiMode.ADVANCED,
                debugTools,
                target
            )
        }
    ) { tpmsState, wheels, usbFault, settings ->
        buildUiState(tpmsState, wheels, usbFault, settings)
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

    /** Header tap while the fault is [UsbFault.NO_PERMISSION]: ask for USB permission again (even after a denial). */
    fun requestUsbPermission() {
        runCatching { usbPermissionRequester.requestExplicitly() }
            .onFailure { debugLog.exception("MainScreen", it, "request USB permission") }
    }

    private fun buildUiState(
        tpmsState: TpmsState,
        wheels: Map<String, TireSensor>,
        usbFault: UsbFault?,
        settings: MainSettingsBundle
    ): MainUiState {
        return try {
            val slots = WheelLayout.allSlots(settings.showSpareWheel)
            // Placement comes from the repository; only the slot list (spare shown or not) is local.
            val wheelSlots = slots.map { wheels[it] }
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
                wheelSlots = wheelSlots,
                wheelSlotLabels = slots,
                wheelStaleMinutes = wheelSlots.map { SensorFreshness.staleWheelMinutes(it, wheelProtocol, now) },
                wheelMuted = wheelSlots.map { SensorFreshness.isWheelMuted(it, wheelProtocol, now) },
                pressureUnit = settings.pressureUnit,
                targetPressureKpa = settings.targetPressureKpa,
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
                pressureUnit = settings.pressureUnit,
                lastError = error.message ?: error.javaClass.simpleName
            )
        }
    }

    companion object {
        private const val CHECK_NOW_REFRESH_MS = 1500L
        private const val FRESHNESS_TICK_MS = 30_000L
    }
}

private data class MainSettingsBundle(
    val pressureUnit: PressureUnit,
    val showSpareWheel: Boolean,
    val advancedMode: Boolean,
    val debugToolsEnabled: Boolean,
    val targetPressureKpa: Float
)
