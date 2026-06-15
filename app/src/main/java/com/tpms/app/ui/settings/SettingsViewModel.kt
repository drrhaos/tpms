package com.tpms.app.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tpms.app.data.repository.TpmsRepository
import com.tpms.app.data.settings.AlertNotificationPrefs
import com.tpms.app.data.settings.SettingsExporter
import com.tpms.app.data.settings.SettingsStore
import com.tpms.app.data.settings.TeyesChecklist
import com.tpms.app.domain.WheelLayout
import com.tpms.app.domain.model.AlertThresholds
import com.tpms.app.domain.model.DongleProtocolMode
import com.tpms.app.domain.model.PressureUnit
import com.tpms.app.domain.model.SettingsUiMode
import com.tpms.app.startup.TeyesSetupStatusProvider
import com.tpms.app.ui.widget.TpmsWidgetHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: SettingsStore,
    private val repository: TpmsRepository,
    private val teyesSetupStatusProvider: TeyesSetupStatusProvider
) : ViewModel() {

    private val _pressureUnit = MutableStateFlow(PressureUnit.KPA)
    val pressureUnit: StateFlow<PressureUnit> = _pressureUnit.asStateFlow()

    private val _lowPressure = MutableStateFlow(AlertThresholds().lowPressureKpa)
    val lowPressure: StateFlow<Float> = _lowPressure.asStateFlow()

    private val _highPressure = MutableStateFlow(AlertThresholds().highPressureKpa)
    val highPressure: StateFlow<Float> = _highPressure.asStateFlow()

    private val _highTemp = MutableStateFlow(AlertThresholds().highTempCelsius)
    val highTemp: StateFlow<Float> = _highTemp.asStateFlow()

    private val _dongleProtocolMode = MutableStateFlow(DongleProtocolMode.AUTO)
    val dongleProtocolMode: StateFlow<DongleProtocolMode> = _dongleProtocolMode.asStateFlow()

    private val _sensorTimeoutSec = MutableStateFlow(60)
    val sensorTimeoutSec: StateFlow<Int> = _sensorTimeoutSec.asStateFlow()

    private val _staleFrameTimeoutSec = MutableStateFlow(90)
    val staleFrameTimeoutSec: StateFlow<Int> = _staleFrameTimeoutSec.asStateFlow()

    private val _showSpareWheel = MutableStateFlow(false)
    val showSpareWheel: StateFlow<Boolean> = _showSpareWheel.asStateFlow()

    private val _minLiveWheelPressure = MutableStateFlow(100f)
    val minLiveWheelPressure: StateFlow<Float> = _minLiveWheelPressure.asStateFlow()

    private val _wheelMapping = MutableStateFlow<Map<String, String>>(emptyMap())
    val wheelMapping: StateFlow<Map<String, String>> = _wheelMapping.asStateFlow()

    private val _settingsUiMode = MutableStateFlow(SettingsUiMode.USER)
    val settingsUiMode: StateFlow<SettingsUiMode> = _settingsUiMode.asStateFlow()

    private val _alertSoundEnabled = MutableStateFlow(true)
    val alertSoundEnabled: StateFlow<Boolean> = _alertSoundEnabled.asStateFlow()

    private val _alertVibrationEnabled = MutableStateFlow(true)
    val alertVibrationEnabled: StateFlow<Boolean> = _alertVibrationEnabled.asStateFlow()

    private val _importExportMessage = MutableStateFlow<String?>(null)
    val importExportMessage: StateFlow<String?> = _importExportMessage.asStateFlow()

    private val _teyesChecklist = MutableStateFlow(TeyesChecklist())
    val teyesChecklist: StateFlow<TeyesChecklist> = _teyesChecklist.asStateFlow()

    private val _batteryUnrestricted = MutableStateFlow(true)
    val batteryUnrestricted: StateFlow<Boolean> = _batteryUnrestricted.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(true)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _widgetActive = MutableStateFlow(false)
    val widgetActive: StateFlow<Boolean> = _widgetActive.asStateFlow()

    val knownSensorIds: StateFlow<List<String>> = repository.sensors
        .combine(_wheelMapping) { sensors, _ ->
            sensors.keys.sorted()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            settingsStore.awaitLoaded()
            _pressureUnit.value = settingsStore.pressureUnit.value
            loadThresholdsFromStore()
            _dongleProtocolMode.value = settingsStore.dongleProtocolMode.value
            _sensorTimeoutSec.value = (settingsStore.sensorTimeoutMs.value / 1000).toInt()
            _staleFrameTimeoutSec.value = (settingsStore.staleFrameTimeoutMs.value / 1000).toInt()
            _showSpareWheel.value = settingsStore.showSpareWheel.value
            _minLiveWheelPressure.value = settingsStore.minLiveWheelPressureKpa.value
            _wheelMapping.value = settingsStore.wheelMapping.value
            _settingsUiMode.value = settingsStore.settingsUiMode.value
            val alertPrefs = settingsStore.alertNotificationPrefs.value
            _alertSoundEnabled.value = alertPrefs.soundEnabled
            _alertVibrationEnabled.value = alertPrefs.vibrationEnabled
        }
        viewModelScope.launch {
            settingsStore.wheelMapping.collect { _wheelMapping.value = it }
        }
        viewModelScope.launch {
            settingsStore.settingsUiMode.collect { _settingsUiMode.value = it }
        }
        viewModelScope.launch {
            settingsStore.showSpareWheel.collect { _showSpareWheel.value = it }
        }
        viewModelScope.launch {
            settingsStore.teyesChecklist.collect { _teyesChecklist.value = it }
        }
        refreshRuntimeSetupStatus()
    }

    fun refreshRuntimeSetupStatus() {
        _batteryUnrestricted.value = teyesSetupStatusProvider.isBatteryUnrestricted(context)
        _notificationsEnabled.value = teyesSetupStatusProvider.areNotificationsEnabled(context)
        _widgetActive.value = TpmsWidgetHelper.hasActiveWidgets(context)
    }

    fun setTeyesChecklistItem(key: String, checked: Boolean) {
        viewModelScope.launch { settingsStore.setTeyesChecklistItem(key, checked) }
    }

    fun openAppDetails() = TeyesPermissionHelper.openAppDetails(context)
    fun openBatterySettings() = TeyesPermissionHelper.openBatteryOptimization(context)
    fun openNotificationSettings() = TeyesPermissionHelper.openNotificationSettings(context)

    fun pinWidgetToHome(): Boolean {
        val accepted = TpmsWidgetHelper.requestPinToTeyesPanel(context)
        refreshRuntimeSetupStatus()
        return accepted
    }

    private fun loadThresholdsFromStore() {
        val thresholds = settingsStore.thresholds.value
        val unit = _pressureUnit.value
        _lowPressure.value = unit.fromKpa(thresholds.lowPressureKpa)
        _highPressure.value = unit.fromKpa(thresholds.highPressureKpa)
        _highTemp.value = thresholds.highTempCelsius
    }

    fun wheelSlots(): List<String> = WheelLayout.allSlots(_showSpareWheel.value)

    fun setPressureUnit(unit: PressureUnit) {
        if (_pressureUnit.value == unit) return
        val lowKpa = _pressureUnit.value.toKpa(_lowPressure.value)
        val highKpa = _pressureUnit.value.toKpa(_highPressure.value)
        _pressureUnit.value = unit
        _lowPressure.value = unit.fromKpa(lowKpa)
        _highPressure.value = unit.fromKpa(highKpa)
        viewModelScope.launch { settingsStore.setPressureUnit(unit) }
    }

    fun setLowPressure(v: Float) { _lowPressure.value = v }
    fun setHighPressure(v: Float) { _highPressure.value = v }
    fun setHighTemp(v: Float) { _highTemp.value = v }

    fun setDongleProtocolMode(mode: DongleProtocolMode) {
        _dongleProtocolMode.value = mode
        viewModelScope.launch { settingsStore.setDongleProtocolMode(mode) }
    }

    fun setSensorTimeoutSec(seconds: Int) {
        val value = seconds.coerceIn(15, 300)
        _sensorTimeoutSec.value = value
        viewModelScope.launch { settingsStore.setSensorTimeoutMs(value * 1000L) }
    }

    fun setStaleFrameTimeoutSec(seconds: Int) {
        val value = seconds.coerceIn(30, 300)
        _staleFrameTimeoutSec.value = value
        viewModelScope.launch { settingsStore.setStaleFrameTimeoutMs(value * 1000L) }
    }

    fun setShowSpareWheel(enabled: Boolean) {
        _showSpareWheel.value = enabled
        viewModelScope.launch { settingsStore.setShowSpareWheel(enabled) }
    }

    fun setMinLiveWheelPressure(v: Float) {
        val value = v.coerceIn(0f, 500f)
        _minLiveWheelPressure.value = value
        viewModelScope.launch { settingsStore.setMinLiveWheelPressureKpa(value) }
    }

    fun setSettingsUiMode(mode: SettingsUiMode) {
        _settingsUiMode.value = mode
        viewModelScope.launch { settingsStore.setSettingsUiMode(mode) }
    }

    fun setAlertSoundEnabled(enabled: Boolean) {
        _alertSoundEnabled.value = enabled
        persistAlertNotificationPrefs()
    }

    fun setAlertVibrationEnabled(enabled: Boolean) {
        _alertVibrationEnabled.value = enabled
        persistAlertNotificationPrefs()
    }

    private fun persistAlertNotificationPrefs() {
        viewModelScope.launch {
            settingsStore.setAlertNotificationPrefs(
                AlertNotificationPrefs(
                    soundEnabled = _alertSoundEnabled.value,
                    vibrationEnabled = _alertVibrationEnabled.value
                )
            )
        }
    }

    fun cycleWheelMapping(slot: String, availableIds: List<String>) {
        val options = listOf("") + availableIds
        val current = _wheelMapping.value[slot].orEmpty()
        val next = options[(options.indexOf(current).coerceAtLeast(0) + 1) % options.size]
        viewModelScope.launch {
            settingsStore.setWheelMapping(slot, next)
        }
    }

    fun exportSettingsJson(): String {
        val unit = _pressureUnit.value
        return SettingsExporter.export(
            pressureUnit = unit,
            thresholds = AlertThresholds(
                lowPressureKpa = unit.toKpa(_lowPressure.value),
                highPressureKpa = unit.toKpa(_highPressure.value),
                highTempCelsius = _highTemp.value
            ),
            dongleProtocolMode = _dongleProtocolMode.value,
            sensorTimeoutMs = _sensorTimeoutSec.value * 1000L,
            staleFrameTimeoutMs = _staleFrameTimeoutSec.value * 1000L,
            wheelMapping = _wheelMapping.value,
            showSpareWheel = _showSpareWheel.value,
            minLiveWheelPressureKpa = _minLiveWheelPressure.value,
            alertNotificationPrefs = AlertNotificationPrefs(
                soundEnabled = _alertSoundEnabled.value,
                vibrationEnabled = _alertVibrationEnabled.value
            ),
            teyesChecklist = settingsStore.teyesChecklist.value
        )
    }

    fun copySettingsToClipboard() {
        val json = exportSettingsJson()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("tpms_settings", json))
        _importExportMessage.value = context.getString(com.tpms.app.R.string.settings_export_copied)
    }

    fun importSettingsJson(json: String) {
        viewModelScope.launch {
            runCatching {
                val imported = SettingsExporter.import(json.trim())
                settingsStore.applyImported(imported)
                _pressureUnit.value = imported.pressureUnit
                _lowPressure.value = imported.pressureUnit.fromKpa(imported.thresholds.lowPressureKpa)
                _highPressure.value = imported.pressureUnit.fromKpa(imported.thresholds.highPressureKpa)
                _highTemp.value = imported.thresholds.highTempCelsius
                _dongleProtocolMode.value = imported.dongleProtocolMode
                _sensorTimeoutSec.value = (imported.sensorTimeoutMs / 1000).toInt()
                _staleFrameTimeoutSec.value = (imported.staleFrameTimeoutMs / 1000).toInt()
                _showSpareWheel.value = imported.showSpareWheel
                _minLiveWheelPressure.value = imported.minLiveWheelPressureKpa
                _importExportMessage.value = context.getString(com.tpms.app.R.string.settings_import_ok)
            }.onFailure { error ->
                _importExportMessage.value = context.getString(
                    com.tpms.app.R.string.settings_import_failed,
                    error.message ?: "error"
                )
            }
        }
    }

    fun clearImportExportMessage() {
        _importExportMessage.value = null
    }

    suspend fun saveSettings() {
        val unit = _pressureUnit.value
        settingsStore.setPressureUnit(unit)
        settingsStore.setDongleProtocolMode(_dongleProtocolMode.value)
        settingsStore.setSensorTimeoutMs(_sensorTimeoutSec.value * 1000L)
        settingsStore.setStaleFrameTimeoutMs(_staleFrameTimeoutSec.value * 1000L)
        settingsStore.setMinLiveWheelPressureKpa(_minLiveWheelPressure.value)
        settingsStore.setThresholds(
            AlertThresholds(
                lowPressureKpa = unit.toKpa(_lowPressure.value),
                highPressureKpa = unit.toKpa(_highPressure.value),
                highTempCelsius = _highTemp.value
            )
        )
        settingsStore.setAlertNotificationPrefs(
            AlertNotificationPrefs(
                soundEnabled = _alertSoundEnabled.value,
                vibrationEnabled = _alertVibrationEnabled.value
            )
        )
    }

    /** Persist threshold text fields when leaving the settings screen. */
    fun savePendingThresholds() {
        viewModelScope.launch {
            val unit = _pressureUnit.value
            settingsStore.setThresholds(
                AlertThresholds(
                    lowPressureKpa = unit.toKpa(_lowPressure.value),
                    highPressureKpa = unit.toKpa(_highPressure.value),
                    highTempCelsius = _highTemp.value
                )
            )
        }
    }

    fun saveThresholds() {
        viewModelScope.launch { saveSettings() }
    }
}
