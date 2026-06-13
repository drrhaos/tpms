package com.tpms.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.util.Log

class UsbPermissionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_USB_PERMISSION) return

        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
        if (granted) {
            Log.d(TAG, "USB permission granted — waking service")
            TpmsMonitorService.wake(context)
        } else {
            Log.w(TAG, "USB permission denied")
        }
    }

    companion object {
        private const val TAG = "UsbPermissionReceiver"
        const val ACTION_USB_PERMISSION = "com.tpms.app.action.USB_PERMISSION"
    }
}
