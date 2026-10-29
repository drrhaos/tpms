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
import com.tpms.app.service.UsbPermissionRequester
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StartupPermissionCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usbPermissionRequester: UsbPermissionRequester,
    private val debugLog: UsbDebugLog
) {
    /**
     * Run once when the main activity is created. Does not open system settings screens
     * automatically — battery / app-details are opened only from Settings UI.
     */
    fun ensureOnLaunch(
        activity: ComponentActivity,
        notificationPermissionLauncher: ActivityResultLauncher<String>
    ) {
        requestNotificationPermissionIfNeeded(activity, notificationPermissionLauncher)
        requestUsbPermissionIfNeeded()
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

    /**
     * A dongle without permission is asked for through the shared once-per-appearance policy, so
     * opening the app does not stack a second dialog on the service's (or re-ask after a denial;
     * the header tap does that).
     */
    private fun requestUsbPermissionIfNeeded() {
        runCatching { usbPermissionRequester.scanNow() }
            .onFailure { error -> debugLog.exception("App", error, "requestUsbPermissionIfNeeded") }
    }
}
