package com.tpms.app.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tpms.app.data.settings.SettingsStore
import com.tpms.app.startup.TeyesDeviceDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NavHostViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: SettingsStore
) : ViewModel() {

    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination: StateFlow<String?> = _startDestination.asStateFlow()

    init {
        viewModelScope.launch {
            settingsStore.awaitLoaded()
            _startDestination.value = if (
                !settingsStore.onboardingComplete.value &&
                TeyesDeviceDetector.isLikelyTeyesHeadUnit(context)
            ) {
                "onboarding"
            } else {
                "main"
            }
        }
    }
}
