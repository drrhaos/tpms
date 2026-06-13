package com.tpms.app.data.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface

object UsbDeviceInfo {

    fun describe(device: UsbDevice): String = buildString {
        appendLine("Device: ${device.deviceName}")
        appendLine("  VID:PID = ${vidPid(device)}")
        appendLine("  Product = ${device.productName ?: "?"} / ${device.manufacturerName ?: "?"}")
        appendLine("  Class = ${className(device.deviceClass)} (${device.deviceClass})")
        appendLine("  Interfaces = ${device.interfaceCount}")
        for (i in 0 until device.interfaceCount) {
            appendLine(describeInterface(device.getInterface(i)))
        }
    }

    fun describeInterface(iface: UsbInterface): String {
        val endpoints = (0 until iface.endpointCount).joinToString(", ") { j ->
            val ep = iface.getEndpoint(j)
            val dir = if (ep.direction == UsbConstants.USB_DIR_IN) "IN" else "OUT"
            val type = when (ep.type) {
                UsbConstants.USB_ENDPOINT_XFER_BULK -> "BULK"
                UsbConstants.USB_ENDPOINT_XFER_INT -> "INT"
                UsbConstants.USB_ENDPOINT_XFER_ISOC -> "ISOC"
                else -> "CTRL"
            }
            "ep$j:$dir/$type(max=${ep.maxPacketSize})"
        }
        return "  Iface${iface.id}: class=${className(iface.interfaceClass)} " +
            "sub=${iface.interfaceSubclass} proto=${iface.interfaceProtocol} → [$endpoints]"
    }

    fun vidPid(device: UsbDevice): String =
        "%04X:%04X".format(device.vendorId, device.productId)

    fun shortLabel(device: UsbDevice): String {
        val product = device.productName
        return if (!product.isNullOrBlank()) {
            "$product (${vidPid(device)})"
        } else {
            vidPid(device)
        }
    }

    private fun className(cls: Int): String = when (cls) {
        UsbConstants.USB_CLASS_HID -> "HID"
        UsbConstants.USB_CLASS_COMM -> "CDC_COMM"
        0x0A -> "CDC_DATA"
        0xFF -> "VENDOR"
        UsbConstants.USB_CLASS_VENDOR_SPEC -> "VENDOR_SPEC"
        else -> "0x${cls.toString(16)}"
    }
}
