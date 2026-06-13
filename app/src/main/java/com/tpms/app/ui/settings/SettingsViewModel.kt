package com.tpms.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tpms.app.data.settings.SettingsStore
import com.tpms.app.domain.model.AlertThresholds
import com.tpms.app.domain.model.DongleProtocolMode
import com.tpms.app.domain.model.PressureUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore
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

    init {
        viewModelScope.launch {
            settingsStore.awaitLoaded()
            _pressureUnit.value = settingsStore.pressureUnit.value
            val thresholds = settingsStore.thresholds.value
            _lowPressure.value = thresholds.lowPressureKpa
            _highPressure.value = thresholds.highPressureKpa
            _highTemp.value = thresholds.highTempCelsius
            _dongleProtocolMode.value = settingsStore.dongleProtocolMode.value
        }
    }

    fun setPressureUnit(unit: PressureUnit) {
        _pressureUnit.value = unit
    }

    fun setLowPressure(v: Float) { _lowPressure.value = v }
    fun setHighPressure(v: Float) { _highPressure.value = v }
    fun setHighTemp(v: Float) { _highTemp.value = v }

    fun setDongleProtocolMode(mode: DongleProtocolMode) {
        _dongleProtocolMode.value = mode
    }

    fun saveThresholds() {
        viewModelScope.launch {
            settingsStore.setPressureUnit(_pressureUnit.value)
            settingsStore.setDongleProtocolMode(_dongleProtocolMode.value)
            settingsStore.setThresholds(
                AlertThresholds(
                    lowPressureKpa = _lowPressure.value,
                    highPressureKpa = _highPressure.value,
                    highTempCelsius = _highTemp.value
                )
            )
        }
    }
}
