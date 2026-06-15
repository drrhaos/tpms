package com.tpms.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tpms.app.data.repository.TpmsRepository
import com.tpms.app.data.settings.AlertNotificationPrefs
import com.tpms.app.data.settings.SettingsStore
import com.tpms.app.data.settings.TeyesChecklist
import com.tpms.app.domain.WheelLayout
import com.tpms.app.domain.model.AlertThresholds
import com.tpms.app.domain.model.DongleProtocolMode
import com.tpms.app.domain.model.PressureUnit
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val settingsStore: SettingsStore,
    private val repository: TpmsRepository
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

    private val _wheelMapping = MutableStateFlow<Map<String, String>>(emptyMap())
    val wheelMapping: StateFlow<Map<String, String>> = _wheelMapping.asStateFlow()

    private val _teyesChecklist = MutableStateFlow(TeyesChecklist())
    val teyesChecklist: StateFlow<TeyesChecklist> = _teyesChecklist.asStateFlow()

    private val _alertSoundEnabled = MutableStateFlow(true)
    val alertSoundEnabled: StateFlow<Boolean> = _alertSoundEnabled.asStateFlow()

    private val _alertVibrationEnabled = MutableStateFlow(true)
    val alertVibrationEnabled: StateFlow<Boolean> = _alertVibrationEnabled.asStateFlow()

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
            _wheelMapping.value = settingsStore.wheelMapping.value
            _teyesChecklist.value = settingsStore.teyesChecklist.value
            val alertPrefs = settingsStore.alertNotificationPrefs.value
            _alertSoundEnabled.value = alertPrefs.soundEnabled
            _alertVibrationEnabled.value = alertPrefs.vibrationEnabled
        }
        viewModelScope.launch {
            settingsStore.wheelMapping.collect { _wheelMapping.value = it }
        }
        viewModelScope.launch {
            settingsStore.teyesChecklist.collect { _teyesChecklist.value = it }
        }
    }

    private fun loadThresholdsFromStore() {
        val thresholds = settingsStore.thresholds.value
        val unit = _pressureUnit.value
        _lowPressure.value = unit.fromKpa(thresholds.lowPressureKpa)
        _highPressure.value = unit.fromKpa(thresholds.highPressureKpa)
        _highTemp.value = thresholds.highTempCelsius
    }

    fun setPressureUnit(unit: PressureUnit) {
        if (_pressureUnit.value == unit) return
        val lowKpa = _pressureUnit.value.toKpa(_lowPressure.value)
        val highKpa = _pressureUnit.value.toKpa(_highPressure.value)
        _pressureUnit.value = unit
        _lowPressure.value = unit.fromKpa(lowKpa)
        _highPressure.value = unit.fromKpa(highKpa)
    }

    fun setLowPressure(v: Float) { _lowPressure.value = v }
    fun setHighPressure(v: Float) { _highPressure.value = v }
    fun setHighTemp(v: Float) { _highTemp.value = v }

    fun setDongleProtocolMode(mode: DongleProtocolMode) {
        _dongleProtocolMode.value = mode
    }

    fun setSensorTimeoutSec(seconds: Int) {
        _sensorTimeoutSec.value = seconds.coerceIn(15, 300)
    }

    fun setAlertSoundEnabled(enabled: Boolean) {
        _alertSoundEnabled.value = enabled
    }

    fun setAlertVibrationEnabled(enabled: Boolean) {
        _alertVibrationEnabled.value = enabled
    }

    fun cycleWheelMapping(slot: String, availableIds: List<String>) {
        val options = listOf("") + availableIds
        val current = _wheelMapping.value[slot].orEmpty()
        val next = options[(options.indexOf(current).coerceAtLeast(0) + 1) % options.size]
        viewModelScope.launch {
            settingsStore.setWheelMapping(slot, next)
        }
    }

    fun setTeyesChecklistItem(key: String, checked: Boolean) {
        viewModelScope.launch {
            settingsStore.setTeyesChecklistItem(key, checked)
        }
    }

    fun saveThresholds() {
        viewModelScope.launch {
            val unit = _pressureUnit.value
            settingsStore.setPressureUnit(unit)
            settingsStore.setDongleProtocolMode(_dongleProtocolMode.value)
            settingsStore.setSensorTimeoutMs(_sensorTimeoutSec.value * 1000L)
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
    }

    val wheelSlots: List<String> = WheelLayout.ORDER
}
