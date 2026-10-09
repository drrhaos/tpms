package com.tpms.app.data.usb

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.tpms.app.service.UsbPermissionReceiver
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsbPermissionHelper @Inject constructor(
    private val usbManager: UsbManager,
    private val dongleDetector: DongleDetector,
    private val debugLog: UsbDebugLog
) {
    private var lastNoDongleLogCount: Int? = null

    fun findDongle(): UsbDevice? {
        val count = usbManager.deviceList.size
        val device = dongleDetector.findDongle(usbManager.deviceList.values)
        if (device == null && count != lastNoDongleLogCount) {
            lastNoDongleLogCount = count
            debugLog.usb("UsbPermission", "findDongle: no device in list of $count")
        } else if (device != null) {
            lastNoDongleLogCount = null
        }
        return device
    }

    fun hasPermission(device: UsbDevice): Boolean = usbManager.hasPermission(device)

    fun requestPermission(context: Context, device: UsbDevice) {
        if (hasPermission(device)) return
        val intent = Intent(context, UsbPermissionReceiver::class.java).apply {
            action = UsbPermissionReceiver.ACTION_USB_PERMISSION
        }
        // Must be mutable: the system adds EXTRA_PERMISSION_GRANTED to it. The intent is explicit.
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            mutable or PendingIntent.FLAG_UPDATE_CURRENT
        )
        usbManager.requestPermission(device, pendingIntent)
    }
}
