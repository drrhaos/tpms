package com.tpms.app.data.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsbConnection @Inject constructor(
    private val usbManager: UsbManager
) {
    private var connection: UsbDeviceConnection? = null
    private var claimedIface: UsbInterface? = null
    private var inEndpoint: UsbEndpoint? = null

    val isConnected: Boolean get() = connection != null

    fun findDongle(): UsbDevice? =
        usbManager.deviceList.values.firstOrNull { isHidDevice(it) }

    fun hasPermission(device: UsbDevice): Boolean = usbManager.hasPermission(device)

    fun open(device: UsbDevice): Boolean {
        if (!hasPermission(device)) return false
        close()
        val conn = usbManager.openDevice(device) ?: return false
        val iface = device.getInterface(0)
        val endpoint = iface.getEndpoint(0)
        if (endpoint.direction != UsbConstants.USB_DIR_IN) return false

        conn.claimInterface(iface, true)
        connection = conn
        claimedIface = iface
        inEndpoint = endpoint
        return true
    }

    suspend fun read(timeoutMs: Long = READ_TIMEOUT_MS): ByteArray? = withContext(Dispatchers.IO) {
        val conn = connection ?: return@withContext null
        val ep = inEndpoint ?: return@withContext null
        val buf = ByteArray(ep.maxPacketSize.coerceAtMost(64))
        try {
            withTimeout(timeoutMs) {
                val read = conn.bulkTransfer(ep, buf, buf.size, timeoutMs.toInt())
                if (read > 0) buf.copyOf(read) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun close() {
        try {
            claimedIface?.let { connection?.releaseInterface(it) }
            connection?.close()
        } catch (_: Exception) {}
        connection = null
        claimedIface = null
        inEndpoint = null
    }

    companion object {
        const val READ_TIMEOUT_MS = 3000L

        fun isHidDevice(device: UsbDevice): Boolean =
            device.deviceClass == UsbConstants.USB_CLASS_HID ||
                (0 until device.interfaceCount).any { i ->
                    device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_HID
                }
    }
}
