package com.tpms.app.startup

import android.content.Context
import com.tpms.app.data.settings.SettingsStore
import com.tpms.app.data.settings.TeyesChecklist
import com.tpms.app.service.ServiceRunningChecker
import com.tpms.app.service.TpmsMonitorService
import com.tpms.app.ui.widget.TpmsWidgetUpdater
import javax.inject.Inject
import javax.inject.Singleton

data class TeyesSetupStatus(
    val isTeyesDevice: Boolean,
    val checklist: TeyesChecklist,
    val checklistComplete: Boolean,
    val batteryUnrestricted: Boolean,
    val notificationsEnabled: Boolean,
    val serviceRunning: Boolean,
    val widgetActive: Boolean
) {
    val needsAttention: Boolean =
        isTeyesDevice && (
            !checklistComplete ||
                !batteryUnrestricted ||
                !notificationsEnabled ||
                !serviceRunning
            )

    val showFrontAppHint: Boolean = isTeyesDevice && !checklist.frontAppHome
}

@Singleton
class TeyesSetupStatusProvider @Inject constructor(
    private val settingsStore: SettingsStore
) {
    fun current(context: Context): TeyesSetupStatus {
        val isTeyes = TeyesDeviceDetector.isLikelyTeyesHeadUnit(context)
        val checklist = settingsStore.teyesChecklist.value
        val serviceRunning = ServiceRunningChecker.isRunning(context, TpmsMonitorService::class.java)
        return TeyesSetupStatus(
            isTeyesDevice = isTeyes,
            checklist = checklist,
            checklistComplete = settingsStore.isTeyesChecklistComplete(),
            batteryUnrestricted = isBatteryUnrestricted(context),
            notificationsEnabled = areNotificationsEnabled(context),
            serviceRunning = serviceRunning,
            widgetActive = TpmsWidgetUpdater.hasAnyActiveWidget(context)
        )
    }

    fun isBatteryUnrestricted(context: Context): Boolean {
        val pm = context.getSystemService(android.os.PowerManager::class.java) ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun areNotificationsEnabled(context: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return true
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        return manager.areNotificationsEnabled()
    }
}
