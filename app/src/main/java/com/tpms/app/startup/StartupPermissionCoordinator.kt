package com.tpms.app.startup

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import com.tpms.app.data.usb.UsbDebugLog
import com.tpms.app.data.usb.UsbDeviceInfo
import com.tpms.app.data.usb.UsbPermissionHelper
import com.tpms.app.service.UsbPermissionNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StartupPermissionCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usbPermissionHelper: UsbPermissionHelper,
    private val debugLog: UsbDebugLog
) {
    private var usbPermissionRequestedThisSession = false

    /**
     * Run once when the main activity is created. Does not open system settings screens
     * automatically — battery / app-details are opened only from Settings UI.
     */
    fun ensureOnLaunch(
        activity: ComponentActivity,
        notificationPermissionLauncher: ActivityResultLauncher<String>
    ) {
        requestNotificationPermissionIfNeeded(activity, notificationPermissionLauncher)
        requestUsbPermissionIfNeeded(activity, force = true)
    }

    /** Re-request USB permission after a new dongle attach intent. */
    fun ensureAfterUsbAttach(activity: ComponentActivity) {
        usbPermissionRequestedThisSession = false
        requestUsbPermissionIfNeeded(activity, force = true)
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

    private fun requestUsbPermissionIfNeeded(activity: Activity, force: Boolean) {
        if (!force && usbPermissionRequestedThisSession) return
        runCatching {
            val device = usbPermissionHelper.findDongle() ?: return
            if (usbPermissionHelper.hasPermission(device)) {
                UsbPermissionNotifier.dismiss(activity)
                return
            }
            usbPermissionRequestedThisSession = true
            debugLog.usb("App", "Requesting USB permission for ${UsbDeviceInfo.shortLabel(device)}")
            usbPermissionHelper.requestPermission(activity, device)
        }.onFailure { error ->
            debugLog.exception("App", error, "requestUsbPermissionIfNeeded")
        }
    }
}
