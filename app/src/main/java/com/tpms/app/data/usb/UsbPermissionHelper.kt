package com.tpms.app.data.usb

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.tpms.app.service.UsbPermissionReceiver
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsbPermissionHelper @Inject constructor(
    private val usbManager: UsbManager,
    private val dongleDetector: DongleDetector
) {
    fun findDongle(): UsbDevice? = dongleDetector.findDongle(usbManager.deviceList.values)

    fun hasPermission(device: UsbDevice): Boolean = usbManager.hasPermission(device)

    fun requestPermission(context: Context, device: UsbDevice) {
        if (hasPermission(device)) return
        val intent = Intent(context, UsbPermissionReceiver::class.java).apply {
            action = UsbPermissionReceiver.ACTION_USB_PERMISSION
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        usbManager.requestPermission(device, pendingIntent)
    }
}
