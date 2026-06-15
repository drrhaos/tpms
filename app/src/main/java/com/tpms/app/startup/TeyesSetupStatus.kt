package com.tpms.app.startup

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.tpms.app.data.settings.SettingsStore
import com.tpms.app.data.settings.TeyesChecklist
import javax.inject.Inject
import javax.inject.Singleton

data class TeyesSetupStatus(
    val checklist: TeyesChecklist,
    val checklistComplete: Boolean,
    val batteryUnrestricted: Boolean,
    val notificationsEnabled: Boolean
) {
    val needsAttention: Boolean =
        !checklistComplete || !batteryUnrestricted || !notificationsEnabled
}

@Singleton
class TeyesSetupStatusProvider @Inject constructor(
    private val settingsStore: SettingsStore
) {
    fun current(context: Context): TeyesSetupStatus {
        val checklist = settingsStore.teyesChecklist.value
        return TeyesSetupStatus(
            checklist = checklist,
            checklistComplete = settingsStore.isTeyesChecklistComplete(),
            batteryUnrestricted = isBatteryUnrestricted(context),
            notificationsEnabled = areNotificationsEnabled(context)
        )
    }

    fun isBatteryUnrestricted(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun areNotificationsEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled()
    }
}
