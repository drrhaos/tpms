package com.tpms.app.data.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import com.tpms.app.domain.model.DongleProtocol
import com.tpms.app.domain.model.DongleProtocolMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DongleDetector @Inject constructor() {

    fun findDongle(devices: Collection<UsbDevice>): UsbDevice? =
        devices.firstOrNull { isSupportedDongle(it) }

    fun isSupportedDongle(device: UsbDevice): Boolean =
        isHidDevice(device) || isSerialDongle(device)

    fun resolve(device: UsbDevice, mode: DongleProtocolMode): DongleProtocol {
        if (mode != DongleProtocolMode.AUTO) {
            val forced = mode.toProtocol()
            return when (forced) {
                DongleProtocol.HID_GENERIC -> if (isHidDevice(device)) forced else autoDetect(device)
                DongleProtocol.SERIAL_AA55, DongleProtocol.DEELIFE ->
                    if (isSerialDongle(device)) forced else autoDetect(device)
            }
        }
        return autoDetect(device)
    }

    private fun autoDetect(device: UsbDevice): DongleProtocol = when {
        isHidDevice(device) && !isSerialDongle(device) -> DongleProtocol.HID_GENERIC
        isSerialDongle(device) -> DongleProtocol.SERIAL_AA55
        isHidDevice(device) -> DongleProtocol.HID_GENERIC
        else -> DongleProtocol.SERIAL_AA55
    }

    fun isHidDevice(device: UsbDevice): Boolean = UsbConnection.isHidDevice(device)

    fun isSerialDongle(device: UsbDevice): Boolean =
        isCdcAcmDevice(device) || isKnownSerialBridge(device)

    private fun isCdcAcmDevice(device: UsbDevice): Boolean =
        (0 until device.interfaceCount).any { i ->
            val iface = device.getInterface(i)
            iface.interfaceClass == UsbConstants.USB_CLASS_COMM ||
                iface.interfaceClass == USB_CLASS_CDC_DATA
        }

    private fun isKnownSerialBridge(device: UsbDevice): Boolean =
        device.vendorId in KNOWN_SERIAL_VENDOR_IDS

    companion object {
        private const val USB_CLASS_CDC_DATA = 0x0A

        private val KNOWN_SERIAL_VENDOR_IDS = setOf(
            0x1A86, // CH340
            0x10C4, // Silicon Labs CP210x
            0x0403, // FTDI
            0x067B, // Prolific PL2303
        )
    }
}
