package com.tpms.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.tpms.app.data.usb.UsbDebugLog
import com.tpms.app.di.UsbReceiverEntryPoint
import dagger.hilt.android.EntryPointAccessors

class UsbAttachedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            UsbReceiverEntryPoint::class.java
        )
        val debugLog = entryPoint.debugLog()

        runCatching {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> handleAttached(context, intent, entryPoint, debugLog)
                UsbManager.ACTION_USB_DEVICE_DETACHED -> handleDetached(context, intent, entryPoint, debugLog)
            }
        }.onFailure { error ->
            debugLog.exception("UsbReceiver", error, "action=${intent.action}")
        }
    }

    private fun handleAttached(
        context: Context,
        intent: Intent,
        entryPoint: UsbReceiverEntryPoint,
        debugLog: UsbDebugLog
    ) {
        val device = intent.parcelableDevice(UsbManager.EXTRA_DEVICE) ?: return
        Log.d(TAG, "USB device attached: ${device.deviceName}")
        debugLog.usb(TAG, "USB attached: ${device.deviceName}")

        val detector = entryPoint.dongleDetector()
        if (!detector.isSupportedDongle(device)) {
            Log.d(TAG, "Attached device is not a TPMS dongle, ignoring")
            return
        }

        val helper = entryPoint.usbPermissionHelper()
        if (helper.hasPermission(device)) {
            UsbPermissionNotifier.dismiss(context)
            TpmsMonitorService.wake(context)
        } else {
            UsbPermissionNotifier.show(context)
            helper.requestPermission(context, device)
        }
    }

    private fun handleDetached(
        context: Context,
        intent: Intent,
        entryPoint: UsbReceiverEntryPoint,
        debugLog: UsbDebugLog
    ) {
        val device = intent.parcelableDevice(UsbManager.EXTRA_DEVICE) ?: return
        Log.d(TAG, "USB device detached: ${device.deviceName}")
        debugLog.usb(TAG, "USB detached: ${device.deviceName}")

        val detector = entryPoint.dongleDetector()
        if (!detector.isSupportedDongle(device)) return

        val serviceIntent = Intent(context, TpmsMonitorService::class.java).apply {
            action = TpmsMonitorService.ACTION_USB_DETACHED
        }
        context.startService(serviceIntent)
    }

    @Suppress("DEPRECATION")
    private fun Intent.parcelableDevice(key: String): UsbDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, UsbDevice::class.java)
        } else {
            getParcelableExtra(key)
        }

    companion object {
        private const val TAG = "UsbAttachedReceiver"
    }
}
