package com.tpms.app.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tpms.app.data.diagnostics.CrashLogStore
import com.tpms.app.data.repository.TpmsRepository
import com.tpms.app.data.usb.UsbDebugLog
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
    private val repository: TpmsRepository,
    private val crashLogStore: CrashLogStore
) : ViewModel() {

    val logEntries: StateFlow<List<UsbDebugLog.Entry>> = repository.debugLogEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    fun probeRead() {
        viewModelScope.launch {
            runCatching { repository.readSensor() }
            refreshUsbScan()
        }
    }
}
