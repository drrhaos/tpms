package com.tpms.app.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.tpms.app.data.diagnostics.UiBreadcrumbs
import com.tpms.app.data.usb.UsbDebugLog
import com.tpms.app.data.usb.UsbDeviceInfo
import com.tpms.app.data.usb.UsbPermissionHelper
import com.tpms.app.service.TpmsMonitorService
import com.tpms.app.ui.theme.TpmsTheme
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var usbPermissionHelper: UsbPermissionHelper
    @Inject lateinit var debugLog: UsbDebugLog
    @Inject lateinit var uiBreadcrumbs: UiBreadcrumbs

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        runCatching {
            requestNotificationPermissionIfNeeded()
            debugLog.info("App", "TPMS Monitor started")
            requestUsbPermissionIfNeeded()
            TpmsMonitorService.start(this)
        }.onFailure { error ->
            debugLog.error("App", uiBreadcrumbs.describe())
            debugLog.exception("App", error, "onCreate startup")
        }

        setContent {
            TpmsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TpmsNavHost()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            requestUsbPermissionIfNeeded()
            TpmsMonitorService.wake(this)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun requestUsbPermissionIfNeeded() {
        runCatching {
            val device = usbPermissionHelper.findDongle()
            if (device == null) {
                debugLog.warn("App", "No TPMS dongle found at startup — open Debug log for USB scan")
                return
            }
            debugLog.usb("App", "Requesting permission for ${UsbDeviceInfo.shortLabel(device)}")
            usbPermissionHelper.requestPermission(this, device)
        }.onFailure { error ->
            debugLog.exception("App", error, "requestUsbPermissionIfNeeded")
        }
    }

    companion object {
        fun newIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
    }
}
