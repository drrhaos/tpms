package com.tpms.app.ui.settings

import androidx.lifecycle.ViewModel
import com.tpms.app.data.repository.TpmsRepository
import com.tpms.app.domain.model.AlertThresholds
import com.tpms.app.domain.model.PressureUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: TpmsRepository
) : ViewModel() {

    private val _pressureUnit = MutableStateFlow(PressureUnit.KPA)
    val pressureUnit: StateFlow<PressureUnit> = _pressureUnit.asStateFlow()

    private val _lowPressure = MutableStateFlow(repository.getThresholds().lowPressureKpa)
    val lowPressure: StateFlow<Float> = _lowPressure.asStateFlow()

    private val _highPressure = MutableStateFlow(repository.getThresholds().highPressureKpa)
    val highPressure: StateFlow<Float> = _highPressure.asStateFlow()

    private val _highTemp = MutableStateFlow(repository.getThresholds().highTempCelsius)
    val highTemp: StateFlow<Float> = _highTemp.asStateFlow()

    fun setPressureUnit(unit: PressureUnit) {
        _pressureUnit.value = unit
    }

    fun setLowPressure(v: Float) { _lowPressure.value = v }
    fun setHighPressure(v: Float) { _highPressure.value = v }
    fun setHighTemp(v: Float) { _highTemp.value = v }

    fun saveThresholds() {
        repository.updateThresholds(
            AlertThresholds(
                lowPressureKpa = _lowPressure.value,
                highPressureKpa = _highPressure.value,
                highTempCelsius = _highTemp.value
            )
        )
    }
}
