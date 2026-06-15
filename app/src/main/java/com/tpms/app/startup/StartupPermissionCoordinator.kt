package com.tpms.app.startup

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import com.tpms.app.data.usb.UsbDebugLog
import com.tpms.app.data.usb.UsbDeviceInfo
import com.tpms.app.data.usb.UsbPermissionHelper
import com.tpms.app.ui.settings.TeyesPermissionHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StartupPermissionCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usbPermissionHelper: UsbPermissionHelper,
    private val debugLog: UsbDebugLog
) {
    private var batteryPromptedThisSession = false

    fun ensureRuntimePermissions(
        activity: ComponentActivity,
        notificationPermissionLauncher: ActivityResultLauncher<String>
    ) {
        requestNotificationPermissionIfNeeded(activity, notificationPermissionLauncher)
        requestUsbPermissionIfNeeded(activity)
        requestBatteryOptimizationIfNeeded(activity)
    }

    private fun requestNotificationPermissionIfNeeded(
        activity: Activity,
        launcher: ActivityResultLauncher<String>
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun requestUsbPermissionIfNeeded(activity: Activity) {
        runCatching {
            val device = usbPermissionHelper.findDongle() ?: return
            if (usbPermissionHelper.hasPermission(device)) return
            debugLog.usb("App", "Requesting USB permission for ${UsbDeviceInfo.shortLabel(device)}")
            usbPermissionHelper.requestPermission(activity, device)
        }.onFailure { error ->
            debugLog.exception("App", error, "requestUsbPermissionIfNeeded")
        }
    }

    private fun requestBatteryOptimizationIfNeeded(activity: Activity) {
        if (batteryPromptedThisSession) return
        val pm = activity.getSystemService(PowerManager::class.java) ?: return
        if (pm.isIgnoringBatteryOptimizations(activity.packageName)) return
        batteryPromptedThisSession = true
        debugLog.info("App", "Requesting battery optimization exemption")
        TeyesPermissionHelper.openBatteryOptimization(activity)
    }
}
