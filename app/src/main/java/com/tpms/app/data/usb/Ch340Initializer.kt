package com.tpms.app.data.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import android.util.Log

/**
 * Minimal CH340 init for TPMS dongles (100-a1-xl-v01 / USB-100).
 * CH340 uses vendor-class bulk endpoints, not CDC-ACM.
 */
object Ch340Initializer {
    private const val TAG = "Ch340Init"
    private const val CH340_REQ_WRITE_REG = 0x9A
    private const val CH340_REQ_MODEM_CTRL = 0xA4

    fun isCh340Device(device: UsbDevice): Boolean {
        if (device.vendorId in CH340_VENDOR_IDS) return true
        return (0 until device.interfaceCount).any { i ->
            val iface = device.getInterface(i)
            iface.interfaceClass == USB_CLASS_VENDOR &&
                iface.interfaceSubclass == 1 &&
                iface.interfaceProtocol == 2
        }
    }

    fun configure(connection: UsbDeviceConnection, iface: UsbInterface, baudRate: Int = 19200): Boolean {
        return try {
            val modem = connection.controlTransfer(
                USB_RT_HOST_TO_DEVICE,
                CH340_REQ_MODEM_CTRL,
                0x0001,
                0,
                null,
                0,
                1000
            )
            if (modem < 0) Log.w(TAG, "CH340 modem ctrl failed: $modem")

            val divisors = baudDivisors(baudRate) ?: return false
            val baud = connection.controlTransfer(
                USB_RT_HOST_TO_DEVICE,
                CH340_REQ_WRITE_REG,
                divisors.first,
                divisors.second,
                null,
                0,
                1000
            )
            if (baud < 0) {
                Log.w(TAG, "CH340 baud set failed: $baud")
                return false
            }
            Log.d(TAG, "CH340 configured at $baudRate baud")
            true
        } catch (e: Exception) {
            Log.w(TAG, "CH340 configure error", e)
            false
        }
    }

    private fun baudDivisors(baud: Int): Pair<Int, Int>? = when (baud) {
        9600 -> 0xCCC3 to 0x0013
        19200 -> 0xFE8C to 0x0013
        38400 -> 0xFF4C to 0x001A
        115200 -> 0xCC83 to 0x001A
        else -> null
    }

    private const val USB_CLASS_VENDOR = 0xFF
    private const val USB_RT_HOST_TO_DEVICE = 0x40

    private val CH340_VENDOR_IDS = setOf(
        0x1A86, // WCH CH340
        0x4348, // CH9102 clones
        0x1A40, // Terminus
    )
}
