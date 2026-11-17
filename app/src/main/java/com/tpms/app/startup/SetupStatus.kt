package com.tpms.app.startup

import android.content.Context
import com.tpms.app.data.settings.SettingsStore
import com.tpms.app.data.settings.TeyesChecklist
import com.tpms.app.service.NotificationHelper
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
    /** The tyre alert channel is on and may pop up (importance HIGH or more). */
    val alertChannelLoud: Boolean,
    /** "Display over other apps": the red alert window can be drawn (wayfinder alerts 03). */
    val alertWindowAllowed: Boolean = true,
    /** The alarm stream, which carries the alert signal, is not at zero (wayfinder alerts 04). */
    val alarmVolumeOn: Boolean = true,
    val serviceRunning: Boolean,
    val widgetActive: Boolean
) {
    val needsAttention: Boolean =
        !serviceRunning ||
            !batteryUnrestricted ||
            !notificationsEnabled ||
            !alertChannelLoud ||
            !alertWindowAllowed ||
            !alarmVolumeOn ||
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
            alertChannelLoud = NotificationHelper.isAlertChannelLoud(context),
            alertWindowAllowed = android.provider.Settings.canDrawOverlays(context),
            alarmVolumeOn = isAlarmVolumeOn(context),
            serviceRunning = ServiceRunningChecker.isRunning(context, TpmsMonitorService::class.java),
            widgetActive = TpmsWidgetUpdater.hasAnyActiveWidget(context)
        )
    }

    fun isAlarmVolumeOn(context: Context): Boolean {
        val audio = context.getSystemService(android.media.AudioManager::class.java) ?: return true
        return audio.getStreamVolume(android.media.AudioManager.STREAM_ALARM) > 0
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
