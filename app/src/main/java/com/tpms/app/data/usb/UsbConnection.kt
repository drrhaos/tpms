package com.tpms.app.data.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import com.tpms.app.data.settings.SettingsStore
import com.tpms.app.domain.model.DongleProtocol
import javax.inject.Inject
import javax.inject.Singleton

/** Finds and opens dongles. Reading is owned by [DongleSession]. */
@Singleton
class UsbConnection @Inject constructor(
    private val usbManager: UsbManager,
    private val debugLog: UsbDebugLog,
    private val settingsStore: SettingsStore
) {
    fun findDongle(detector: DongleDetector): UsbDevice? =
        try {
            detector.findDongle(
                usbManager.deviceList.values,
                settingsStore.preferredUsbVidPid.value
            )
        } catch (_: Throwable) {
            null
        }

    fun hasPermission(device: UsbDevice): Boolean = usbManager.hasPermission(device)

    fun listAllDevices(): List<UsbDevice> =
        try {
            usbManager.deviceList.values.toList()
        } catch (_: Throwable) {
            emptyList()
        }

    /** True while a device with this path is enumerated. A re-enumerated dongle gets a new path. */
    fun isDevicePresent(deviceName: String): Boolean =
        try {
            usbManager.deviceList.containsKey(deviceName)
        } catch (_: Throwable) {
            false
        }

    fun open(device: UsbDevice, protocol: DongleProtocol): UsbLink? {
        if (!hasPermission(device)) {
            debugLog.warn(TAG, "No USB permission for ${UsbDeviceInfo.shortLabel(device)}")
            return null
        }
        debugLog.usb(TAG, "Opening ${UsbDeviceInfo.shortLabel(device)} as ${protocol.displayName}")
        debugLog.usb(TAG, UsbDeviceInfo.describe(device))

        val conn = usbManager.openDevice(device) ?: run {
            debugLog.error(TAG, "usbManager.openDevice() returned null")
            return null
        }
        // A dongle pulled mid-open throws from claim / control transfers: close, never leak the fd.
        val claim = try {
            when (protocol) {
                DongleProtocol.HID_GENERIC -> claimHid(conn, device)
                DongleProtocol.SERIAL_AA55, DongleProtocol.DEELIFE -> claimSerial(conn, device)
            }
        } catch (e: Exception) {
            debugLog.error(TAG, "Claim threw: ${e.message}")
            null
        }
        if (claim == null) {
            debugLog.error(TAG, "Failed to claim endpoints for ${protocol.displayName}")
            try {
                conn.close()
            } catch (_: Throwable) {}
            return null
        }
        debugLog.info(TAG, "Connected, inEp=${claim.inEndpoint.address}, outEp=${claim.outEndpoint?.address}")
        return UsbLink(
            connection = conn,
            deviceName = device.deviceName,
            vendorId = device.vendorId,
            productId = device.productId,
            inEndpoint = claim.inEndpoint,
            outEndpoint = claim.outEndpoint,
            interfaces = claim.interfaces
        )
    }

    private class Claim(
        val inEndpoint: UsbEndpoint,
        val outEndpoint: UsbEndpoint?,
        val interfaces: List<UsbInterface>
    )

    private fun claimSerial(conn: UsbDeviceConnection, device: UsbDevice): Claim? {
        if (Ch340Initializer.isCh340Device(device)) {
            claimVendorSerial(conn, device)?.let {
                debugLog.usb(TAG, "Opened as CH340 vendor serial")
                return it
            }
        }
        claimCdcSerial(conn, device)?.let {
            debugLog.usb(TAG, "Opened as CDC-ACM serial")
            return it
        }
        claimVendorSerial(conn, device)?.let {
            debugLog.usb(TAG, "Opened as vendor bulk serial")
            return it
        }
        debugLog.error(TAG, "Serial open failed: no CDC or vendor bulk interface")
        return null
    }

    private fun claimHid(conn: UsbDeviceConnection, device: UsbDevice): Claim? {
        val iface = findHidInterface(device) ?: return null
        val endpoint = findBulkInEndpoint(iface) ?: findInterruptInEndpoint(iface) ?: return null
        if (!conn.claimInterface(iface, true)) {
            debugLog.error(TAG, "HID claimInterface failed")
            return null
        }
        return Claim(
            inEndpoint = endpoint,
            outEndpoint = findBulkOutEndpoint(iface) ?: findInterruptOutEndpoint(iface),
            interfaces = listOf(iface)
        )
    }

    private fun claimCdcSerial(conn: UsbDeviceConnection, device: UsbDevice): Claim? {
        var commIface: UsbInterface? = null
        var dataIface: UsbInterface? = null

        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            when (iface.interfaceClass) {
                UsbConstants.USB_CLASS_COMM -> commIface = iface
                USB_CLASS_CDC_DATA -> dataIface = iface
            }
        }
        val data = dataIface ?: return null
        val inEp = findBulkInEndpoint(data) ?: return null

        val claimed = mutableListOf<UsbInterface>()
        commIface?.let {
            if (!conn.claimInterface(it, true)) return null
            claimed.add(it)
            setLineCoding(conn, it, SERIAL_BAUD_RATE)
        }
        if (!conn.claimInterface(data, true)) {
            claimed.forEach { runCatching { conn.releaseInterface(it) } }
            return null
        }
        claimed.add(data)
        return Claim(inEp, findBulkOutEndpoint(data), claimed)
    }

    private fun claimVendorSerial(conn: UsbDeviceConnection, device: UsbDevice): Claim? {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            val inEp = findBulkInEndpoint(iface) ?: continue
            if (!conn.claimInterface(iface, true)) {
                debugLog.warn(TAG, "claimInterface(${iface.id}) failed")
                continue
            }
            if (Ch340Initializer.isCh340Device(device)) {
                val configured = Ch340Initializer.configure(conn, iface, SERIAL_BAUD_RATE)
                debugLog.usb(TAG, "CH340 init ${if (configured) "OK" else "FAILED"}")
            }
            return Claim(inEp, findBulkOutEndpoint(iface), listOf(iface))
        }
        return null
    }

    private fun findHidInterface(device: UsbDevice): UsbInterface? {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_HID) return iface
        }
        return null
    }

    private fun findBulkInEndpoint(iface: UsbInterface): UsbEndpoint? =
        findEndpoint(iface, UsbConstants.USB_ENDPOINT_XFER_BULK, UsbConstants.USB_DIR_IN)

    private fun findBulkOutEndpoint(iface: UsbInterface): UsbEndpoint? =
        findEndpoint(iface, UsbConstants.USB_ENDPOINT_XFER_BULK, UsbConstants.USB_DIR_OUT)

    private fun findInterruptInEndpoint(iface: UsbInterface): UsbEndpoint? =
        findEndpoint(iface, UsbConstants.USB_ENDPOINT_XFER_INT, UsbConstants.USB_DIR_IN)

    private fun findInterruptOutEndpoint(iface: UsbInterface): UsbEndpoint? =
        findEndpoint(iface, UsbConstants.USB_ENDPOINT_XFER_INT, UsbConstants.USB_DIR_OUT)

    private fun findEndpoint(iface: UsbInterface, type: Int, direction: Int): UsbEndpoint? {
        for (i in 0 until iface.endpointCount) {
            val ep = iface.getEndpoint(i)
            if (ep.type == type && ep.direction == direction) return ep
        }
        return null
    }

    private fun setLineCoding(conn: UsbDeviceConnection, commIface: UsbInterface, baudRate: Int) {
        val baud = byteArrayOf(
            (baudRate and 0xFF).toByte(),
            (baudRate shr 8 and 0xFF).toByte(),
            (baudRate shr 16 and 0xFF).toByte(),
            (baudRate shr 24 and 0xFF).toByte(),
            0,
            0,
            8
        )
        try {
            conn.controlTransfer(
                USB_RT_HOST_TO_DEVICE or USB_CLASS_COMM,
                CDC_SET_LINE_CODING,
                0,
                commIface.id,
                baud,
                baud.size,
                1000
            )
        } catch (_: Throwable) {}
    }

    companion object {
        private const val TAG = "UsbConnection"
        private const val SERIAL_BAUD_RATE = 19200
        private const val USB_CLASS_CDC_DATA = 0x0A
        private const val USB_CLASS_COMM = 0x02
        private const val USB_RT_HOST_TO_DEVICE = 0x21
        private const val CDC_SET_LINE_CODING = 0x20

        fun isHidDevice(device: UsbDevice): Boolean =
            device.deviceClass == UsbConstants.USB_CLASS_HID ||
                (0 until device.interfaceCount).any { i ->
                    device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_HID
                }
    }
}
