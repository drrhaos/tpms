package com.tpms.app.data.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import com.tpms.app.domain.model.DongleProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsbConnection @Inject constructor(
    private val usbManager: UsbManager,
    private val debugLog: UsbDebugLog
) {
    private val usbLock = Any()

    private var connection: UsbDeviceConnection? = null
    private var connectedDeviceName: String? = null
    private var connectedVendorId: Int = -1
    private var connectedProductId: Int = -1
    private val claimedInterfaces = mutableListOf<UsbInterface>()
    private var inEndpoint: UsbEndpoint? = null
    private var outEndpoint: UsbEndpoint? = null
    private var activeTransport: UsbTransport? = null

    val isConnected: Boolean
        get() = synchronized(usbLock) { connection != null }

    fun isSameDevice(device: UsbDevice): Boolean = synchronized(usbLock) {
        connection != null &&
            connectedVendorId == device.vendorId &&
            connectedProductId == device.productId
    }

    /** True if our open device still appears in the current USB device list (by path or VID:PID). */
    fun isOpenDevicePresent(): Boolean {
        synchronized(usbLock) {
            if (connection == null) return false
        }
        return try {
            val list = usbManager.deviceList
            val name = connectedDeviceName
            if (name != null && list.containsKey(name)) return true
            if (connectedVendorId < 0) return false
            list.values.any {
                it.vendorId == connectedVendorId && it.productId == connectedProductId
            }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Quick bulk-transfer probe. Returns true when the link is still alive (>= 0 bytes or timeout),
     * false when the kernel reports a dead endpoint (-1).
     */
    fun probe(timeoutMs: Int = 200): Boolean = synchronized(usbLock) {
        val conn = connection ?: return false
        val ep = inEndpoint ?: return false
        val buf = ByteArray(ep.maxPacketSize.coerceAtLeast(64))
        return try {
            conn.bulkTransfer(ep, buf, buf.size, timeoutMs) >= 0
        } catch (_: Throwable) {
            false
        }
    }

    fun findDongle(detector: DongleDetector): UsbDevice? =
        try {
            detector.findDongle(usbManager.deviceList.values)
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

    fun open(device: UsbDevice, protocol: DongleProtocol): Boolean = synchronized(usbLock) {
        if (!hasPermission(device)) {
            debugLog.warn(TAG, "No USB permission for ${UsbDeviceInfo.shortLabel(device)}")
            return false
        }
        closeLocked()
        debugLog.usb(TAG, "Opening ${UsbDeviceInfo.shortLabel(device)} as ${protocol.displayName}")
        debugLog.usb(TAG, UsbDeviceInfo.describe(device))

        val conn = usbManager.openDevice(device) ?: run {
            debugLog.error(TAG, "usbManager.openDevice() returned null")
            return false
        }

        val transport = when (protocol) {
            DongleProtocol.HID_GENERIC -> UsbTransport.HID
            DongleProtocol.SERIAL_AA55, DongleProtocol.DEELIFE -> UsbTransport.SERIAL
        }

        val opened = when (transport) {
            UsbTransport.HID -> openHid(conn, device)
            UsbTransport.SERIAL -> openSerial(conn, device)
        }

        if (!opened) {
            debugLog.error(TAG, "Failed to claim endpoints for ${protocol.displayName}")
            try {
                conn.close()
            } catch (_: Throwable) {}
            return false
        }

        connection = conn
        connectedDeviceName = device.deviceName
        connectedVendorId = device.vendorId
        connectedProductId = device.productId
        activeTransport = transport
        debugLog.info(
            TAG,
            "Connected via $activeTransport, inEp=${inEndpoint?.address}, outEp=${outEndpoint?.address}"
        )
        true
    }

    suspend fun read(timeoutMs: Long = READ_TIMEOUT_MS): ByteArray? = withContext(Dispatchers.IO) {
        synchronized(usbLock) {
            val conn = connection ?: return@withContext null
            val ep = inEndpoint ?: return@withContext null
            val buf = ByteArray(ep.maxPacketSize.coerceAtLeast(64))
            try {
                withTimeout(timeoutMs) {
                    val read = conn.bulkTransfer(ep, buf, buf.size, timeoutMs.toInt())
                    when {
                        read > 0 -> {
                            val data = buf.copyOf(read)
                            val hex = data.joinToString(" ") { "%02X".format(it) }
                            debugLog.raw(TAG, "RX ${data.size}b: $hex")
                            data
                        }
                        read == 0 -> {
                            debugLog.warn(TAG, "bulkTransfer returned 0 bytes")
                            null
                        }
                        else -> {
                            debugLog.warn(TAG, "bulkTransfer failed: $read")
                            null
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                debugLog.error(TAG, "Read error: ${e.message}")
                null
            }
        }
    }

    suspend fun write(data: ByteArray, timeoutMs: Long = WRITE_TIMEOUT_MS): Boolean =
        withContext(Dispatchers.IO) {
            synchronized(usbLock) {
                val conn = connection ?: return@withContext false
                val ep = outEndpoint ?: return@withContext false
                try {
                    withTimeout(timeoutMs) {
                        val written = conn.bulkTransfer(ep, data, data.size, timeoutMs.toInt())
                        val ok = written == data.size
                        val hex = data.joinToString(" ") { "%02X".format(it) }
                        debugLog.raw(TAG, "TX ${if (ok) "OK" else "FAIL"} ${data.size}b: $hex")
                        ok
                    }
                } catch (e: Exception) {
                    debugLog.error(TAG, "Write error: ${e.message}")
                    false
                }
            }
        }

    fun close() = synchronized(usbLock) {
        closeLocked()
    }

    private fun closeLocked() {
        if (connection != null) debugLog.info(TAG, "USB connection closed")
        try {
            claimedInterfaces.forEach { iface ->
                try {
                    connection?.releaseInterface(iface)
                } catch (_: Throwable) {}
            }
            connection?.close()
        } catch (_: Throwable) {}
        connection = null
        connectedDeviceName = null
        connectedVendorId = -1
        connectedProductId = -1
        claimedInterfaces.clear()
        inEndpoint = null
        outEndpoint = null
        activeTransport = null
    }

    private fun openSerial(conn: UsbDeviceConnection, device: UsbDevice): Boolean {
        if (Ch340Initializer.isCh340Device(device) && openVendorSerial(conn, device)) {
            debugLog.usb(TAG, "Opened as CH340 vendor serial")
            return true
        }
        if (openCdcSerial(conn, device)) {
            debugLog.usb(TAG, "Opened as CDC-ACM serial")
            return true
        }
        if (openVendorSerial(conn, device)) {
            debugLog.usb(TAG, "Opened as vendor bulk serial")
            return true
        }
        debugLog.error(TAG, "Serial open failed: no CDC or vendor bulk interface")
        return false
    }

    private fun openHid(conn: UsbDeviceConnection, device: UsbDevice): Boolean {
        val iface = findHidInterface(device) ?: return false
        val endpoint = findBulkInEndpoint(iface) ?: findInterruptInEndpoint(iface) ?: return false
        if (!conn.claimInterface(iface, true)) {
            debugLog.error(TAG, "HID claimInterface failed")
            return false
        }
        claimedInterfaces.add(iface)
        inEndpoint = endpoint
        outEndpoint = findBulkOutEndpoint(iface) ?: findInterruptOutEndpoint(iface)
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

        if (dataIface == null) return false
        val iface = dataIface

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

    private fun openVendorSerial(conn: UsbDeviceConnection, device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            val inEp = findBulkInEndpoint(iface) ?: continue
            if (!conn.claimInterface(iface, true)) {
                debugLog.warn(TAG, "claimInterface(${iface.id}) failed")
                continue
            }
            claimedInterfaces.add(iface)
            inEndpoint = inEp
            outEndpoint = findBulkOutEndpoint(iface)

            if (Ch340Initializer.isCh340Device(device)) {
                val configured = Ch340Initializer.configure(conn, iface, SERIAL_BAUD_RATE)
                debugLog.usb(TAG, "CH340 init ${if (configured) "OK" else "FAILED"}")
            }
            return true
        }
        return false
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

    private enum class UsbTransport { HID, SERIAL }

    companion object {
        private const val TAG = "UsbConnection"
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
