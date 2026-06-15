package com.tpms.app.ui.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import com.tpms.app.data.diagnostics.UiBreadcrumbs
import com.tpms.app.data.usb.UsbDebugLog
import com.tpms.app.service.TpmsMonitorService
import com.tpms.app.startup.StartupPermissionCoordinator
import com.tpms.app.ui.embedded.EmbeddedWindowDetector
import com.tpms.app.ui.embedded.EmbeddedWindowProvider
import com.tpms.app.ui.theme.TpmsTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var startupPermissions: StartupPermissionCoordinator
    @Inject lateinit var debugLog: UsbDebugLog
    @Inject lateinit var uiBreadcrumbs: UiBreadcrumbs

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onResume() {
        super.onResume()
        runCatching {
            TpmsMonitorService.start(this)
        }.onFailure { error ->
            debugLog.exception("App", error, "ensure service on resume")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!EmbeddedWindowDetector.isLaunchedFromFrontApp(this)) {
            enableEdgeToEdge()
        }

        runCatching {
            debugLog.info("App", "TPMS Monitor started")
            startupPermissions.ensureOnLaunch(this, notificationPermissionLauncher)
            TpmsMonitorService.start(this)
        }.onFailure { error ->
            debugLog.error("App", uiBreadcrumbs.describe())
            debugLog.exception("App", error, "onCreate startup")
        }

        setContent {
            TpmsTheme {
                EmbeddedWindowProvider {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        TpmsNavHost()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            startupPermissions.ensureAfterUsbAttach(this)
            TpmsMonitorService.wake(this)
        }
    }

    companion object {
        fun newIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }

        fun fullScreenIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EmbeddedWindowDetector.EXTRA_FORCE_FULLSCREEN, true)
            }
    }
}
