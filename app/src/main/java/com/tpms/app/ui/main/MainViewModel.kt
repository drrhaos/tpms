package com.tpms.app.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tpms.app.data.repository.TpmsRepository
import com.tpms.app.domain.model.PressureUnit
import com.tpms.app.domain.model.TpmsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val repository: TpmsRepository
) : AndroidViewModel(application) {

    val state: StateFlow<TpmsState> = repository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TpmsState.Disconnected)

    val sensors = repository.sensors
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val pressureUnit = MutableStateFlow(PressureUnit.KPA)
    val lowThreshold = MutableStateFlow(repository.getThresholds().lowPressureKpa)
    val highThreshold = MutableStateFlow(repository.getThresholds().highPressureKpa)
    val highTempThreshold = MutableStateFlow(repository.getThresholds().highTempCelsius)

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage

    fun checkNow() {
        viewModelScope.launch {
            repository.readSensor()
        }
    }
}
