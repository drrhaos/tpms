package com.tpms.app.data.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import com.tpms.app.domain.model.DongleProtocol
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
    private val claimedInterfaces = mutableListOf<UsbInterface>()
    private var inEndpoint: UsbEndpoint? = null
    private var outEndpoint: UsbEndpoint? = null
    private var activeTransport: UsbTransport? = null

    val isConnected: Boolean get() = connection != null

    fun findDongle(detector: DongleDetector): UsbDevice? =
        detector.findDongle(usbManager.deviceList.values)

    fun hasPermission(device: UsbDevice): Boolean = usbManager.hasPermission(device)

    fun open(device: UsbDevice, protocol: DongleProtocol): Boolean {
        if (!hasPermission(device)) return false
        close()
        val transport = when (protocol) {
            DongleProtocol.HID_GENERIC -> UsbTransport.HID
            DongleProtocol.SERIAL_AA55, DongleProtocol.DEELIFE -> UsbTransport.CDC_SERIAL
        }
        val conn = usbManager.openDevice(device) ?: return false
        val opened = when (transport) {
            UsbTransport.HID -> openHid(conn, device)
            UsbTransport.CDC_SERIAL -> openCdcSerial(conn, device)
        }
        if (!opened) {
            conn.close()
            return false
        }
        connection = conn
        activeTransport = transport
        return true
    }

    suspend fun read(timeoutMs: Long = READ_TIMEOUT_MS): ByteArray? = withContext(Dispatchers.IO) {
        val conn = connection ?: return@withContext null
        val ep = inEndpoint ?: return@withContext null
        val buf = ByteArray(ep.maxPacketSize.coerceAtLeast(64))
        try {
            withTimeout(timeoutMs) {
                val read = conn.bulkTransfer(ep, buf, buf.size, timeoutMs.toInt())
                if (read > 0) buf.copyOf(read) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun write(data: ByteArray, timeoutMs: Long = WRITE_TIMEOUT_MS): Boolean =
        withContext(Dispatchers.IO) {
            val conn = connection ?: return@withContext false
            val ep = outEndpoint ?: return@withContext false
            try {
                withTimeout(timeoutMs) {
                    val written = conn.bulkTransfer(ep, data, data.size, timeoutMs.toInt())
                    written == data.size
                }
            } catch (_: Exception) {
                false
            }
        }

    fun close() {
        try {
            claimedInterfaces.forEach { connection?.releaseInterface(it) }
            connection?.close()
        } catch (_: Exception) {}
        connection = null
        claimedInterfaces.clear()
        inEndpoint = null
        outEndpoint = null
        activeTransport = null
    }

    private fun openHid(conn: UsbDeviceConnection, device: UsbDevice): Boolean {
        val iface = findHidInterface(device) ?: return false
        val endpoint = findBulkInEndpoint(iface) ?: return false
        if (!conn.claimInterface(iface, true)) return false
        claimedInterfaces.add(iface)
        inEndpoint = endpoint
        outEndpoint = findBulkOutEndpoint(iface)
        return true
    }

    private fun openCdcSerial(conn: UsbDeviceConnection, device: UsbDevice): Boolean {
        var commIface: UsbInterface? = null
        var dataIface: UsbInterface? = null

        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            when (iface.interfaceClass) {
                UsbConstants.USB_CLASS_COMM -> commIface = iface
                USB_CLASS_CDC_DATA -> dataIface = iface
            }
        }

        if (dataIface == null) {
            dataIface = findBulkDataInterface(device)
        }

        val iface = dataIface ?: return false
        commIface?.let {
            if (!conn.claimInterface(it, true)) return false
            claimedInterfaces.add(it)
            setLineCoding(conn, it, SERIAL_BAUD_RATE)
        }
        if (!conn.claimInterface(iface, true)) return false
        claimedInterfaces.add(iface)

        inEndpoint = findBulkInEndpoint(iface) ?: return false
        outEndpoint = findBulkOutEndpoint(iface)
        return true
    }

    private fun findHidInterface(device: UsbDevice): UsbInterface? {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_HID) return iface
        }
        return null
    }

    private fun findBulkDataInterface(device: UsbDevice): UsbInterface? {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (findBulkInEndpoint(iface) != null) return iface
        }
        return null
    }

    private fun findBulkInEndpoint(iface: UsbInterface): UsbEndpoint? {
        for (i in 0 until iface.endpointCount) {
            val ep = iface.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                ep.direction == UsbConstants.USB_DIR_IN
            ) {
                return ep
            }
        }
        return null
    }

    private fun findBulkOutEndpoint(iface: UsbInterface): UsbEndpoint? {
        for (i in 0 until iface.endpointCount) {
            val ep = iface.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                ep.direction == UsbConstants.USB_DIR_OUT
            ) {
                return ep
            }
        }
        return null
    }

    private fun setLineCoding(conn: UsbDeviceConnection, commIface: UsbInterface, baudRate: Int) {
        val baud = byteArrayOf(
            (baudRate and 0xFF).toByte(),
            (baudRate shr 8 and 0xFF).toByte(),
            (baudRate shr 16 and 0xFF).toByte(),
            (baudRate shr 24 and 0xFF).toByte(),
            0, // 1 stop bit
            0, // no parity
            8  // 8 data bits
        )
        conn.controlTransfer(
            USB_RT_HOST_TO_DEVICE or USB_CLASS_COMM,
            CDC_SET_LINE_CODING,
            0,
            commIface.id,
            baud,
            baud.size,
            1000
        )
    }

    private enum class UsbTransport { HID, CDC_SERIAL }

    companion object {
        const val READ_TIMEOUT_MS = 3000L
        private const val WRITE_TIMEOUT_MS = 1000L
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
