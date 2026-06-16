package com.tpms.app.ui.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tpms.app.data.settings.SettingsStore
import com.tpms.app.service.TpmsMonitorService
import com.tpms.app.startup.HeadUnitSupport
import com.tpms.app.ui.settings.TeyesPermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

object OnboardingSteps {
    const val USB = 0
    const val PERMISSIONS = 1
    const val TEYES_PERMISSIONS = 2
    const val TEYES_WIDGET = 3

    fun lastStep(teyes: Boolean): Int = if (teyes) TEYES_WIDGET else PERMISSIONS
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: SettingsStore,
    headUnitSupport: HeadUnitSupport
) : ViewModel() {

    val showTeyesSteps: Boolean = headUnitSupport.isTeyesHeadUnit()

    private val lastStep: Int = OnboardingSteps.lastStep(showTeyesSteps)

    private val _step = MutableStateFlow(OnboardingSteps.USB)
    val step: StateFlow<Int> = _step.asStateFlow()

    fun nextStep() {
        _step.value = (_step.value + 1).coerceAtMost(lastStep)
    }

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
