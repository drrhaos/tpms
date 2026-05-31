package com.tpms.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log

class UsbAttachedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE) ?: return
            Log.d(TAG, "USB device attached: ${device.deviceName}")
            TpmsMonitorService.start(context)
        }
    }

    companion object {
        private const val TAG = "UsbAttachedReceiver"
    }
}
