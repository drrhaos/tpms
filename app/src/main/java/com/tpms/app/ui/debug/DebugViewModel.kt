package com.tpms.app.ui.debug

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tpms.app.data.diagnostics.CrashLogStore
import com.tpms.app.data.repository.TpmsRepository
import com.tpms.app.data.settings.SettingsStore
import com.tpms.app.data.usb.UsbDebugLog
import com.tpms.app.service.TpmsMonitorService
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DebugViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TpmsRepository,
    private val crashLogStore: CrashLogStore,
    settingsStore: SettingsStore
) : ViewModel() {

    val logEntries: StateFlow<List<UsbDebugLog.Entry>> = repository.debugLogEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val logRecordingEnabled: StateFlow<Boolean> = settingsStore.diagnosticLogEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _usbScan = MutableStateFlow("")
    val usbScan: StateFlow<String> = _usbScan.asStateFlow()

    val hasPersistedCrash: Boolean
        get() = !crashLogStore.read().isNullOrBlank()

    init {
        refreshUsbScan()
    }

    fun refreshUsbScan() {
        viewModelScope.launch {
            _usbScan.value = repository.scanUsbDevices()
        }
    }

    fun clearLog() {
        repository.clearDebugLog()
    }

    fun exportLog(): String = repository.exportDebugLog()

    fun exportFullReport(): String = repository.exportFullReport()

    /** Reopens the dongle; reading itself is owned by the monitoring service. */
    fun probeRead() {
        viewModelScope.launch {
            runCatching { TpmsMonitorService.probeUsb(context, force = true) }
            refreshUsbScan()
        }
    }
}
