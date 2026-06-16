package com.tpms.app.ui.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tpms.app.R
import com.tpms.app.data.settings.SettingsStore
import com.tpms.app.service.TpmsMonitorService
import com.tpms.app.startup.TeyesDeviceDetector
import com.tpms.app.ui.settings.TeyesPermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TeyesOnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: SettingsStore
) : ViewModel() {

    val showTeyesSteps: Boolean = TeyesDeviceDetector.isLikelyTeyesHeadUnit(context)

    private val lastStep: Int get() = if (showTeyesSteps) 3 else 1

    private val _step = MutableStateFlow(0)
    val step: StateFlow<Int> = _step.asStateFlow()

    fun nextStep() {
        _step.value = (_step.value + 1).coerceAtMost(lastStep)
    }

    fun isLastStep(): Boolean = _step.value >= lastStep

    fun requestUsbPermission() {
        TpmsMonitorService.start(context)
        nextStep()
    }

    fun openNotifications() {
        TeyesPermissionHelper.openNotificationSettings(context)
    }

    fun openTeyesSettings() {
        TeyesPermissionHelper.openTeyesSettings(context)
    }

    fun openFrontApp() {
        TeyesPermissionHelper.openFrontApp(context)
    }

    fun markFrontAppHome() {
        viewModelScope.launch {
            settingsStore.setTeyesChecklistItem("frontapp_home", true)
        }
    }

    fun openBattery() {
        TeyesPermissionHelper.openBatteryOptimization(context)
    }

    fun complete(onDone: () -> Unit) {
        viewModelScope.launch {
            settingsStore.setOnboardingComplete(true)
            onDone()
        }
    }
}
