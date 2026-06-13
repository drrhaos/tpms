package com.tpms.app.data.repository

import android.hardware.usb.UsbDevice
import com.tpms.app.data.db.SensorDao
import com.tpms.app.data.db.SensorReading
import com.tpms.app.data.settings.SettingsStore
import com.tpms.app.data.usb.DongleDetector
import com.tpms.app.data.usb.TpmsProtocolRouter
import com.tpms.app.data.usb.UsbConnection
import com.tpms.app.data.usb.UsbDebugLog
import com.tpms.app.data.usb.UsbDeviceInfo
import com.tpms.app.domain.model.AlertThresholds
import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.DongleProtocol
import com.tpms.app.domain.model.TireSensor
import com.tpms.app.domain.model.TpmsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TpmsRepository @Inject constructor(
    private val usbConnection: UsbConnection,
    private val dongleDetector: DongleDetector,
    private val protocolRouter: TpmsProtocolRouter,
    private val sensorDao: SensorDao,
    private val settingsStore: SettingsStore,
    private val debugLog: UsbDebugLog
) {
    private val _state = MutableStateFlow<TpmsState>(TpmsState.Disconnected)
    val state = _state.asStateFlow()

    private val _sensors = MutableStateFlow<Map<String, TireSensor>>(emptyMap())
    val sensors = _sensors.asStateFlow()

    private val _alertTrigger = MutableSharedFlow<TireSensor>(extraBufferCapacity = 4)
    val alertTrigger = _alertTrigger.asSharedFlow()

    private val lastSeen = mutableMapOf<String, Long>()
    private var onAlertCallback: ((TireSensor) -> Unit)? = null
    private var activeProtocol: DongleProtocol? = null

    fun onAlert(callback: (TireSensor) -> Unit) {
        onAlertCallback = callback
    }

    fun findDongle(): UsbDevice? = usbConnection.findDongle(dongleDetector)

    fun isUsbConnected(): Boolean = usbConnection.isConnected

    fun isOpenDevicePresent(): Boolean =
        runCatching { usbConnection.isOpenDevicePresent() }.getOrDefault(false)

    fun probeUsbConnection(): Boolean =
        runCatching { usbConnection.probe() }.getOrDefault(false)

    fun scanUsbDevices(): String = buildString {
        val devices = usbConnection.listAllDevices()
        appendLine("USB devices attached: ${devices.size}")
        appendLine()
        if (devices.isEmpty()) {
            appendLine("No USB devices found.")
            appendLine("Check cable, OTG/host port, and that the dongle LED is on.")
        }
        devices.forEach { device ->
            try {
                appendLine("─── ${UsbDeviceInfo.shortLabel(device)} ───")
                append(UsbDeviceInfo.describe(device))
                appendLine("  Permission: ${if (hasUsbPermission(device)) "GRANTED" else "NOT GRANTED"}")
                appendLine("  Supported: ${dongleDetector.isSupportedDongle(device)}")
                appendLine("  Reason: ${dongleDetector.rejectionReason(device)}")
            } catch (e: Exception) {
                appendLine("─── (device unreadable) ───")
                appendLine("  Error: ${e.message}")
            }
            appendLine()
        }
        val selected = findDongle()
        appendLine("Selected dongle: ${selected?.let { UsbDeviceInfo.shortLabel(it) } ?: "none"}")
        appendLine("Active protocol: ${activeProtocol()?.displayName ?: "none"}")
    }

    fun debugLogEntries() = debugLog.entries

    fun clearDebugLog() = debugLog.clear()

    fun exportDebugLog(): String {
        val header = buildString {
            appendLine(scanUsbDevices())
            appendLine("--- Event log ---")
        }
        return header + debugLog.exportText()
    }

    fun hasUsbPermission(device: UsbDevice): Boolean = usbConnection.hasPermission(device)

    fun activeProtocol(): DongleProtocol? = activeProtocol

    suspend fun openDongle(device: UsbDevice): Boolean {
        return try {
            if (usbConnection.isSameDevice(device)) {
                return true
            }
            val protocol = dongleDetector.resolve(device, settingsStore.dongleProtocolMode.value)
            val opened = usbConnection.open(device, protocol)
            if (opened) {
                activeProtocol = protocol
                protocolRouter.onDongleOpened(protocol, usbConnection)
                debugLog.info("Repository", "Dongle opened with ${protocol.displayName}")
            } else {
                debugLog.error("Repository", "Failed to open dongle ${UsbDeviceInfo.vidPid(device)}")
            }
            opened
        } catch (e: Exception) {
            debugLog.error("Repository", "openDongle crashed: ${e.message}")
            false
        }
    }

    suspend fun readSensor(): TireSensor? {
        return try {
            val raw = usbConnection.read() ?: return null
            val timestamp = System.currentTimeMillis()
            val parsed = protocolRouter.parse(raw, timestamp) { checkAlert(it) }
            if (parsed.isNotEmpty()) {
                debugLog.info(
                    "Repository",
                    parsed.joinToString { "${it.label} %.0f kPa %.0f°C".format(it.pressureKpa, it.temperatureCelsius) }
                )
            }
            var last: TireSensor? = null
            for (sensor in parsed) {
                applySensorUpdate(sensor)
                last = sensor
            }
            last
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            debugLog.warn("Repository", "readSensor failed: ${e.message}")
            null
        }
    }

    fun checkSensorTimeouts(now: Long = System.currentTimeMillis()): List<TireSensor> {
        val lostSensors = mutableListOf<TireSensor>()
        val updated = _sensors.value.toMutableMap()
        var changed = false

        for ((id, sensor) in _sensors.value) {
            val seen = lastSeen[id] ?: continue
            if (now - seen > SENSOR_TIMEOUT_MS && sensor.alertType != AlertType.SENSOR_LOST) {
                val lost = sensor.copy(alertType = AlertType.SENSOR_LOST)
                updated[id] = lost
                lostSensors.add(lost)
                changed = true
                emitAlert(lost)
            }
        }

        if (changed) {
            _sensors.value = updated
            refreshConnectionState(now)
        }
        return lostSensors
    }

    fun updateState(state: TpmsState) {
        _state.value = state
    }

    fun getThresholds(): AlertThresholds = settingsStore.thresholds.value

    fun closeUsb() {
        try {
            usbConnection.close()
            protocolRouter.onDongleClosed()
            activeProtocol = null
            lastSeen.clear()
            _sensors.value = emptyMap()
            _state.value = TpmsState.Disconnected
        } catch (e: Exception) {
            debugLog.warn("Repository", "closeUsb failed: ${e.message}")
            activeProtocol = null
            _state.value = TpmsState.Disconnected
        }
    }

    fun recentHistory(limit: Int = 20): Flow<List<SensorReading>> =
        sensorDao.recentReadings(limit)

    private suspend fun applySensorUpdate(sensor: TireSensor) {
        try {
            lastSeen[sensor.id] = sensor.timestamp
            val updated = _sensors.value.toMutableMap()
            updated[sensor.id] = sensor
            _sensors.value = updated
            runCatching { sensorDao.insert(sensor.toReading()) }
                .onFailure { debugLog.warn("Repository", "DB insert failed: ${it.message}") }
            if (sensor.isAlert) {
                emitAlert(sensor)
            }
            refreshConnectionState(sensor.timestamp)
        } catch (e: Exception) {
            debugLog.warn("Repository", "applySensorUpdate failed: ${e.message}")
        }
    }

    private fun refreshConnectionState(timestamp: Long) {
        try {
            val sensorList = _sensors.value.values.toList()
            if (sensorList.isEmpty()) return

            val alerting = sensorList.firstOrNull { it.isAlert && it.alertType != null }
            _state.value = if (alerting != null) {
                TpmsState.Alert(
                    sensor = alerting,
                    type = alerting.alertType!!,
                    previousState = TpmsState.Connected(sensorList, timestamp)
                )
            } else {
                TpmsState.Connected(sensorList, timestamp)
            }
        } catch (e: Exception) {
            debugLog.warn("Repository", "refreshConnectionState failed: ${e.message}")
        }
    }

    private fun checkAlert(sensor: TireSensor): AlertType? {
        return try {
            val thresholds = settingsStore.thresholds.value
            when {
                sensor.pressureKpa < thresholds.lowPressureKpa -> AlertType.LOW_PRESSURE
                sensor.pressureKpa > thresholds.highPressureKpa -> AlertType.HIGH_PRESSURE
                sensor.temperatureCelsius > thresholds.highTempCelsius -> AlertType.HIGH_TEMP
                sensor.batteryPercent < 10 -> AlertType.BATTERY_LOW
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun emitAlert(sensor: TireSensor) {
        try {
            onAlertCallback?.invoke(sensor)
            _alertTrigger.tryEmit(sensor)
        } catch (e: Exception) {
            debugLog.warn("Repository", "emitAlert failed: ${e.message}")
        }
    }

    private fun TireSensor.toReading() = SensorReading(
        sensorId = id,
        pressureKpa = pressureKpa,
        temperatureCelsius = temperatureCelsius,
        batteryPercent = batteryPercent,
        alertType = alertType?.name,
        timestamp = timestamp
    )

    companion object {
        private const val SENSOR_TIMEOUT_MS = 60_000L
    }
}
