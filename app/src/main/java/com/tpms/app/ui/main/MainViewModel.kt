package com.tpms.app.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tpms.app.data.repository.TpmsRepository
import com.tpms.app.data.settings.SettingsStore
import com.tpms.app.domain.model.PressureUnit
import com.tpms.app.domain.model.TpmsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val repository: TpmsRepository,
    settingsStore: SettingsStore
) : AndroidViewModel(application) {

    val state: StateFlow<TpmsState> = repository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TpmsState.Disconnected)

    val sensors = repository.sensors
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val pressureUnit: StateFlow<PressureUnit> = settingsStore.pressureUnit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PressureUnit.KPA)

    fun checkNow() {
        viewModelScope.launch {
            runCatching { repository.readSensor() }
        }
    }
}
