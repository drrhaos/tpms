package com.tpms.app.service

import android.app.Activity
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import com.tpms.app.di.UsbReceiverEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * Invisible trampoline for `USB_DEVICE_ATTACHED`. It holds the activity intent-filter so the
 * system's "Use by default for this USB device" association (and the permission it keeps across
 * re-enumeration after sleep) points here instead of at MainActivity: an attach wakes the service
 * like the attach broadcast does and finishes at once, so the app UI does not pop up.
 *
 * Finishes in onCreate, before any frame is drawn; the translucent theme keeps a missed finish
 * harmless (Theme.NoDisplay would crash on API 23+ if onResume were ever reached).
 */
class UsbAttachActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            handleAttach()
        } finally {
            finish()
            suppressTransition()
        }
    }

    private fun handleAttach() {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            UsbReceiverEntryPoint::class.java
        )
        val debugLog = entryPoint.debugLog()
        runCatching {
            if (intent?.action != UsbManager.ACTION_USB_DEVICE_ATTACHED) return
            val device = intent.usbDevice() ?: return
            if (!entryPoint.dongleDetector().isSupportedDongle(device)) return
            val granted = entryPoint.usbPermissionHelper().hasPermission(device)
            debugLog.usb(TAG, "Attach activity: ${device.deviceName} permission=$granted")
            // Shared once-per-appearance policy: the attach broadcast for the same device asks no second time.
            entryPoint.usbPermissionRequester().onDongleScanned(device)
            TpmsMonitorService.wake(this, deviceAttached = true)
        }.onFailure { error ->
            debugLog.exception(TAG, error, "attach activity")
        }
    }

    @Suppress("DEPRECATION")
    private fun suppressTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            overridePendingTransition(0, 0)
        }
    }

    @Suppress("DEPRECATION")
    private fun android.content.Intent.usbDevice(): UsbDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }

    companion object {
        private const val TAG = "UsbAttachActivity"
    }
}
