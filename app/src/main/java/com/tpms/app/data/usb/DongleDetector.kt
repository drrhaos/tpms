package com.tpms.app.data.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface
import com.tpms.app.domain.model.DongleProtocol
import com.tpms.app.domain.model.DongleProtocolMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DongleDetector @Inject constructor(
    private val debugLog: UsbDebugLog
) {

    fun findDongle(devices: Collection<UsbDevice>): UsbDevice? {
        val candidates = devices.mapNotNull { device ->
            val score = scoreDevice(device)
            if (score > 0) device to score else null
        }
        if (candidates.isEmpty()) {
            debugLog.usb(TAG, "No supported dongle among ${devices.size} USB device(s)")
            devices.forEach { debugLog.usb(TAG, "  rejected: ${UsbDeviceInfo.shortLabel(it)} — ${rejectionReason(it)}") }
            return null
        }
        val best = candidates.maxBy { it.second }.first
        debugLog.usb(TAG, "Selected dongle: ${UsbDeviceInfo.shortLabel(best)} (score=${scoreDevice(best)})")
        return best
    }

    fun isSupportedDongle(device: UsbDevice): Boolean = scoreDevice(device) > 0

    fun resolve(device: UsbDevice, mode: DongleProtocolMode): DongleProtocol {
        if (mode != DongleProtocolMode.AUTO) {
            val forced = mode.toProtocol()
            debugLog.usb(TAG, "Protocol forced: ${forced.displayName} for ${UsbDeviceInfo.vidPid(device)}")
            return forced
        }
        return autoDetect(device).also {
            debugLog.usb(TAG, "Protocol auto-detected: ${it.displayName} for ${UsbDeviceInfo.vidPid(device)}")
        }
    }

    fun rejectionReason(device: UsbDevice): String {
        val reasons = mutableListOf<String>()
        if (isHidDevice(device)) reasons.add("HID")
        if (isCdcAcmDevice(device)) reasons.add("CDC")
        if (isKnownSerialBridge(device)) reasons.add("known-serial-VID")
        if (isVendorBulkSerial(device)) reasons.add("vendor-bulk")
        if (Ch340Initializer.isCh340Device(device)) reasons.add("CH340")
        return if (reasons.isEmpty()) "no HID/serial interface" else "matched: ${reasons.joinToString()}"
    }

    private fun autoDetect(device: UsbDevice): DongleProtocol = when {
        isHidDevice(device) && !isSerialCapable(device) -> DongleProtocol.HID_GENERIC
        isSerialCapable(device) -> when {
            Ch340Initializer.isCh340Device(device) -> DongleProtocol.SERIAL_AA55
            else -> DongleProtocol.SERIAL_AA55
        }
        isHidDevice(device) -> DongleProtocol.HID_GENERIC
        else -> DongleProtocol.SERIAL_AA55
    }

    private fun scoreDevice(device: UsbDevice): Int {
        var score = 0
        if (Ch340Initializer.isCh340Device(device)) score += 120
        if (isKnownSerialBridge(device)) score += 100
        if (isCdcAcmDevice(device)) score += 80
        if (isVendorBulkSerial(device)) score += 70
        if (isHidDevice(device)) score += 50
        val name = (device.productName ?: "").lowercase()
        if (name.contains("tpms") || name.contains("100-a1") || name.contains("ch340") || name.contains("serial")) {
            score += 30
        }
        return score
    }

    fun isHidDevice(device: UsbDevice): Boolean = UsbConnection.isHidDevice(device)

    fun isSerialCapable(device: UsbDevice): Boolean =
        isCdcAcmDevice(device) || isKnownSerialBridge(device) || isVendorBulkSerial(device)

    fun isSerialDongle(device: UsbDevice): Boolean = isSerialCapable(device)

    private fun isCdcAcmDevice(device: UsbDevice): Boolean =
        (0 until device.interfaceCount).any { i ->
            val iface = device.getInterface(i)
            iface.interfaceClass == UsbConstants.USB_CLASS_COMM ||
                iface.interfaceClass == USB_CLASS_CDC_DATA
        }

    private fun isVendorBulkSerial(device: UsbDevice): Boolean =
        (0 until device.interfaceCount).any { i ->
            hasBulkInEndpoint(device.getInterface(i))
        }

    private fun isKnownSerialBridge(device: UsbDevice): Boolean =
        device.vendorId in KNOWN_SERIAL_VENDOR_IDS ||
            device.productId in KNOWN_CH340_PRODUCT_IDS

    private fun hasBulkInEndpoint(iface: UsbInterface): Boolean {
        for (i in 0 until iface.endpointCount) {
            val ep = iface.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                ep.direction == UsbConstants.USB_DIR_IN
            ) {
                return true
            }
        }
        return false
    }

    companion object {
        private const val TAG = "DongleDetector"
        private const val USB_CLASS_CDC_DATA = 0x0A

        private val KNOWN_SERIAL_VENDOR_IDS = setOf(
            0x1A86, // CH340
            0x10C4, // CP210x
            0x0403, // FTDI
            0x067B, // Prolific
            0x4348, // CH9102
            0x1A40, // Terminus
        )

        private val KNOWN_CH340_PRODUCT_IDS = setOf(
            0x7523, // CH340T
            0x5523,
            0x5740,
            0x7584,
            0x55D8,
            0x5512,
        )
    }
}
