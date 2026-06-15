package com.tpms.app.ui.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tpms.app.data.settings.SettingsStore
import com.tpms.app.service.TpmsMonitorService
import com.tpms.app.ui.settings.TeyesPermissionHelper
import com.tpms.app.ui.widget.TpmsWidgetHelper
import com.tpms.app.ui.widget.WidgetPinResult
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

    private val _step = MutableStateFlow(0)
    val step: StateFlow<Int> = _step.asStateFlow()

    fun nextStep() {
        _step.value = (_step.value + 1).coerceAtMost(3)
    }

    fun requestUsbPermission() {
        TpmsMonitorService.start(context)
        nextStep()
    }

    fun openNotifications() {
        TeyesPermissionHelper.openNotificationSettings(context)
        nextStep()
    }

    fun openBattery() {
        TeyesPermissionHelper.openBatteryOptimization(context)
        nextStep()
    }

    fun pinWidget() {
        val result = TpmsWidgetHelper.requestPinPanel(context)
        TpmsWidgetHelper.showPinResultToast(context, result)
        if (result != WidgetPinResult.DECLINED) {
            nextStep()
        }
    }

    fun complete(onDone: () -> Unit) {
        viewModelScope.launch {
            settingsStore.setOnboardingComplete(true)
            onDone()
        }
    }
}
