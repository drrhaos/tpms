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

    fun scanUsbDevices(): String = buildString {
        val devices = usbConnection.listAllDevices()
        appendLine("USB devices attached: ${devices.size}")
        appendLine()
        if (devices.isEmpty()) {
            appendLine("No USB devices found.")
            appendLine("Check cable, OTG/host port, and that the dongle LED is on.")
        }
        devices.forEach { device ->
            appendLine("─── ${UsbDeviceInfo.shortLabel(device)} ───")
            append(UsbDeviceInfo.describe(device))
            appendLine("  Permission: ${if (hasUsbPermission(device)) "GRANTED" else "NOT GRANTED"}")
            appendLine("  Supported: ${dongleDetector.isSupportedDongle(device)}")
            appendLine("  Reason: ${dongleDetector.rejectionReason(device)}")
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
        val protocol = dongleDetector.resolve(device, settingsStore.dongleProtocolMode.value)
        val opened = usbConnection.open(device, protocol)
        if (opened) {
            activeProtocol = protocol
            protocolRouter.onDongleOpened(protocol, usbConnection)
            debugLog.info("Repository", "Dongle opened with ${protocol.displayName}")
        } else {
            debugLog.error("Repository", "Failed to open dongle ${UsbDeviceInfo.vidPid(device)}")
        }
        return opened
    }

    suspend fun readSensor(): TireSensor? {
        val raw = usbConnection.read() ?: return null
        val timestamp = System.currentTimeMillis()
        val parsed = protocolRouter.parse(raw, timestamp) { checkAlert(it) }
        var last: TireSensor? = null
        for (sensor in parsed) {
            applySensorUpdate(sensor)
            last = sensor
        }
        return last
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
        usbConnection.close()
        protocolRouter.onDongleClosed()
        activeProtocol = null
        lastSeen.clear()
        _sensors.value = emptyMap()
        _state.value = TpmsState.Disconnected
    }

    fun recentHistory(limit: Int = 20): Flow<List<SensorReading>> =
        sensorDao.recentReadings(limit)

    private suspend fun applySensorUpdate(sensor: TireSensor) {
        lastSeen[sensor.id] = sensor.timestamp
        val updated = _sensors.value.toMutableMap()
        updated[sensor.id] = sensor
        _sensors.value = updated
        sensorDao.insert(sensor.toReading())
        if (sensor.isAlert) {
            emitAlert(sensor)
        }
        refreshConnectionState(sensor.timestamp)
    }

    private fun refreshConnectionState(timestamp: Long) {
        val sensorList = _sensors.value.values.toList()
        if (sensorList.isEmpty()) return

        val alerting = sensorList.firstOrNull { it.isAlert }
        _state.value = if (alerting != null) {
            TpmsState.Alert(
                sensor = alerting,
                type = alerting.alertType!!,
                previousState = TpmsState.Connected(sensorList, timestamp)
            )
        } else {
            TpmsState.Connected(sensorList, timestamp)
        }
    }

    private fun checkAlert(sensor: TireSensor): AlertType? {
        val thresholds = settingsStore.thresholds.value
        return when {
            sensor.pressureKpa < thresholds.lowPressureKpa -> AlertType.LOW_PRESSURE
            sensor.pressureKpa > thresholds.highPressureKpa -> AlertType.HIGH_PRESSURE
            sensor.temperatureCelsius > thresholds.highTempCelsius -> AlertType.HIGH_TEMP
            sensor.batteryPercent < 10 -> AlertType.BATTERY_LOW
            else -> null
        }
    }

    private fun emitAlert(sensor: TireSensor) {
        onAlertCallback?.invoke(sensor)
        _alertTrigger.tryEmit(sensor)
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
