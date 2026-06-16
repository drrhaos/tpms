package com.tpms.app.startup

import android.content.Context
import com.tpms.app.data.settings.SettingsStore
import com.tpms.app.data.settings.TeyesChecklist
import com.tpms.app.service.ServiceRunningChecker
import com.tpms.app.service.TpmsMonitorService
import com.tpms.app.ui.widget.TpmsWidgetUpdater
import javax.inject.Inject
import javax.inject.Singleton

data class SetupStatus(
    val isTeyesDevice: Boolean,
    val checklist: TeyesChecklist,
    val checklistComplete: Boolean,
    val batteryUnrestricted: Boolean,
    val notificationsEnabled: Boolean,
    val serviceRunning: Boolean,
    val widgetActive: Boolean
) {
    val needsAttention: Boolean =
        !serviceRunning ||
            !batteryUnrestricted ||
            !notificationsEnabled ||
            (isTeyesDevice && !checklistComplete)

    val showFrontAppHint: Boolean = isTeyesDevice && !checklist.frontAppHome
}

@Singleton
class SetupStatusProvider @Inject constructor(
    private val settingsStore: SettingsStore,
    private val headUnitSupport: HeadUnitSupport
) {
    fun current(context: Context): SetupStatus {
        val checklist = settingsStore.teyesChecklist.value
        return SetupStatus(
            isTeyesDevice = headUnitSupport.isTeyesHeadUnit(),
            checklist = checklist,
            checklistComplete = settingsStore.isTeyesChecklistComplete(),
            batteryUnrestricted = isBatteryUnrestricted(context),
            notificationsEnabled = areNotificationsEnabled(context),
            serviceRunning = ServiceRunningChecker.isRunning(context, TpmsMonitorService::class.java),
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
