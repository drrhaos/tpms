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
        if (intent.action != UsbManager.ACTION_USB_DEVICE_ATTACHED) return

        val device = intent.parcelableDevice(UsbManager.EXTRA_DEVICE) ?: return
        Log.d(TAG, "USB device attached: ${device.deviceName}")

        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val helper = UsbPermissionHelper(usbManager, DongleDetector(UsbDebugLog()), UsbDebugLog())

        if (helper.hasPermission(device)) {
            TpmsMonitorService.start(context)
        } else {
            helper.requestPermission(context, device)
        }
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
