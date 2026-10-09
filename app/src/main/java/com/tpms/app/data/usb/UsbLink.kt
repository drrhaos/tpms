package com.tpms.app.data.usb

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface

/** An opened dongle: connection, claimed interfaces and endpoints. Owned by one [DongleSession]. */
class UsbLink(
    val connection: UsbDeviceConnection,
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val inEndpoint: UsbEndpoint,
    val outEndpoint: UsbEndpoint?,
    private val interfaces: List<UsbInterface>
) {
    val vidPid: String get() = "%04X:%04X".format(vendorId, productId)

    /** Short blocking write; only used for handshake frames. */
    fun write(data: ByteArray, timeoutMs: Int = WRITE_TIMEOUT_MS): Boolean {
        val ep = outEndpoint ?: return false
        return try {
            connection.bulkTransfer(ep, data, data.size, timeoutMs) == data.size
        } catch (_: Throwable) {
            false
        }
    }

    /** Releasing the interfaces aborts pending transfers on them (URBs end with -ESHUTDOWN). */
    fun releaseInterfaces() {
        interfaces.forEach { iface ->
            try {
                connection.releaseInterface(iface)
            } catch (_: Throwable) {}
        }
    }

    /** Must not be called while a read may still be blocked on this connection. */
    fun close() {
        try {
            connection.close()
        } catch (_: Throwable) {}
    }

    companion object {
        private const val WRITE_TIMEOUT_MS = 1000
    }
}
