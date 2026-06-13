package com.tpms.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.tpms.app.data.usb.DongleDetector
import com.tpms.app.data.usb.UsbDebugLog
import com.tpms.app.data.usb.UsbPermissionHelper

class UsbAttachedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> handleAttached(context, intent)
            UsbManager.ACTION_USB_DEVICE_DETACHED -> handleDetached(context, intent)
        }
    }

    private fun handleAttached(context: Context, intent: Intent) {
        val device = intent.parcelableDevice(UsbManager.EXTRA_DEVICE) ?: return
        Log.d(TAG, "USB device attached: ${device.deviceName}")

        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val detector = DongleDetector(UsbDebugLog())
        if (!detector.isSupportedDongle(device)) {
            Log.d(TAG, "Attached device is not a TPMS dongle, ignoring")
            return
        }

        val helper = UsbPermissionHelper(usbManager, detector, UsbDebugLog())

        if (helper.hasPermission(device)) {
            TpmsMonitorService.wake(context)
        } else {
            helper.requestPermission(context, device)
        }
    }

    private fun handleDetached(context: Context, intent: Intent) {
        val device = intent.parcelableDevice(UsbManager.EXTRA_DEVICE) ?: return
        Log.d(TAG, "USB device detached: ${device.deviceName}")

        val detector = DongleDetector(UsbDebugLog())
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
