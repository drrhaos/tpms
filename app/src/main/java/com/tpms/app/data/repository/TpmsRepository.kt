package com.tpms.app.data.repository

import android.hardware.usb.UsbDevice
import com.tpms.app.data.db.SensorDao
import com.tpms.app.data.db.SensorReading
import com.tpms.app.data.diagnostics.DiagnosticsExporter
import com.tpms.app.data.diagnostics.ServiceHealth
import com.tpms.app.data.diagnostics.UiBreadcrumbs
import com.tpms.app.data.usb.DongleDetector
import com.tpms.app.data.usb.TpmsProtocolRouter
import com.tpms.app.data.usb.UsbConnection
import com.tpms.app.data.usb.UsbDebugLog
import com.tpms.app.data.usb.UsbDeviceInfo
import com.tpms.app.data.settings.SettingsStore
import com.tpms.app.domain.AlertEvaluator
import com.tpms.app.domain.ConnectionHealthPolicy
import com.tpms.app.domain.model.AlertThresholds
import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.DongleProtocol
import com.tpms.app.domain.model.TireSensor
import com.tpms.app.domain.model.TpmsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val debugLog: UsbDebugLog,
    private val diagnosticsExporter: DiagnosticsExporter,
    private val uiBreadcrumbs: UiBreadcrumbs,
    private val serviceHealth: ServiceHealth
) {
    private val _state = MutableStateFlow<TpmsState>(TpmsState.Disconnected)
    val state = _state.asStateFlow()

    private val _sensors = MutableStateFlow<Map<String, TireSensor>>(emptyMap())
    val sensors = _sensors.asStateFlow()

    private val lastSeen = mutableMapOf<String, Long>()
    private var onAlertCallback: ((TireSensor) -> Unit)? = null
    private var onSensorNormalCallback: ((String) -> Unit)? = null

    @Volatile
    private var lastFrameAtMs: Long = 0L

    @Volatile
    private var lastValidFrameAtMs: Long = 0L

    @Volatile
    private var dongleOpenedAtMs: Long = 0L

    @Volatile
    private var isReconnecting: Boolean = false

    init {
        usbConnection.onReadTimeout = {
            serviceHealth.recordReadTimeout()
        }
    }

    fun onAlert(callback: (TireSensor) -> Unit) {
        onAlertCallback = callback
    }

    fun onSensorNormal(callback: (String) -> Unit) {
        onSensorNormalCallback = callback
    }

    fun findDongle(): UsbDevice? = usbConnection.findDongle(dongleDetector)

    fun isUsbConnected(): Boolean = usbConnection.isConnected

    fun isOpenDevicePresent(): Boolean =
        runCatching { usbConnection.isOpenDevicePresent() }.getOrDefault(false)

    fun probeUsbConnection(): Boolean =
        runCatching { usbConnection.probe() }.getOrDefault(false)

    fun isReconnecting(): Boolean = isReconnecting

    fun isProtocolUnhealthy(now: Long = System.currentTimeMillis()): Boolean =
        ConnectionHealthPolicy.isProtocolUnhealthy(
            dongleOpenedAtMs = dongleOpenedAtMs,
            lastValidFrameAtMs = lastValidFrameAtMs,
            now = now
        )

    fun shouldReconnectStaleFrame(now: Long = System.currentTimeMillis()): Boolean =
        ConnectionHealthPolicy.shouldReconnectStaleFrame(
            isUsbConnected = isUsbConnected(),
            lastValidFrameAtMs = lastValidFrameAtMs,
            staleThresholdMs = settingsStore.staleFrameTimeoutMs.value,
            now = now
        )

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

    fun exportDebugLog(): String = buildString {
        appendLine(scanUsbDevices())
        appendLine("--- Event log ---")
        append(debugLog.eventsText())
    }

    fun exportFullReport(): String = diagnosticsExporter.exportFullReport(
        tpmsState = _state.value,
        sensors = _sensors.value,
        wheelMapping = settingsStore.wheelMapping.value,
        showSpareWheel = settingsStore.showSpareWheel.value,
        pressureUnit = settingsStore.pressureUnit.value,
        usbScan = scanUsbDevices(),
        serviceStatusLine = serviceStatusLine(),
        serviceHealth = serviceHealth
    )

    fun serviceStatusLine(): String {
        val protocol = activeProtocol()?.displayName ?: "no protocol"
        val dongle = usbConnection.connectedVidPid() ?: "no dongle"
        val frameAge = lastFrameAgeSeconds()?.let { "${it}s ago" } ?: "never"
        val validAge = lastValidFrameAgeSeconds()?.let { "${it}s ago" } ?: "never"
        val stateLabel = when (val s = _state.value) {
            is TpmsState.Disconnected -> if (isReconnecting) "reconnecting" else "disconnected"
            is TpmsState.Connecting -> "connecting"
            is TpmsState.Connected -> "connected"
            is TpmsState.Alert -> "alert"
        }
        val protocolWarn = if (isProtocolUnhealthy()) " protocol_warn" else ""
        val health = serviceHealth.healthLine(
            frameAgeSec = lastValidFrameAgeSeconds(),
            isReconnecting = isReconnecting,
            protocolUnhealthy = isProtocolUnhealthy()
        )
        return "$stateLabel · $protocol · $dongle · frame $frameAge · valid $validAge$protocolWarn · screen ${uiBreadcrumbs.lastScreen} · $health"
    }

    fun lastFrameAtMs(): Long = lastFrameAtMs

    fun lastValidFrameAtMs(): Long = lastValidFrameAtMs

    fun lastPollCompletedAtMs(): Long = serviceHealth.lastPollCompletedAtMs

    fun lastFrameAgeSeconds(): Long? {
        if (lastFrameAtMs <= 0L) return null
        return (System.currentTimeMillis() - lastFrameAtMs) / 1000
    }

    fun lastValidFrameAgeSeconds(): Long? {
        if (lastValidFrameAtMs <= 0L) return null
        return (System.currentTimeMillis() - lastValidFrameAtMs) / 1000
    }

    fun knownSensorIds(): List<String> = _sensors.value.keys.sorted()

    fun hasUsbPermission(device: UsbDevice): Boolean = usbConnection.hasPermission(device)

    fun activeProtocol(): DongleProtocol? = protocolRouter.activeProtocol()

    suspend fun openDongle(device: UsbDevice): Boolean {
        return try {
            if (usbConnection.isSameDevice(device)) {
                isReconnecting = false
                return true
            }
            val protocol = dongleDetector.resolve(device, settingsStore.dongleProtocolMode.value)
            val opened = usbConnection.open(device, protocol)
            if (opened) {
                protocolRouter.onDongleOpened(protocol, usbConnection)
                dongleOpenedAtMs = System.currentTimeMillis()
                isReconnecting = false
                debugLog.info("Repository", "Dongle opened with ${protocol.displayName}")
            } else {
                debugLog.error("Repository", "Failed to open dongle ${UsbDeviceInfo.vidPid(device)}")
            }
            opened
        } catch (e: Exception) {
            debugLog.error("Repository", "openDongle crashed: ${e.message}")
            serviceHealth.recordError("openDongle: ${e.message}")
            false
        }
    }

    suspend fun readSensor(): TireSensor? {
        return try {
            val raw = usbConnection.read() ?: return null
            lastFrameAtMs = System.currentTimeMillis()
            val timestamp = lastFrameAtMs
            val thresholds = settingsStore.thresholds.value
            val minLivePressure = settingsStore.minLiveWheelPressureKpa.value
            val parsed = protocolRouter.parse(raw, timestamp, {
                AlertEvaluator.evaluate(it, thresholds)
            }, minLivePressure)
            if (parsed.isNotEmpty()) {
                lastValidFrameAtMs = timestamp
                debugLog.info(
                    "Repository",
                    parsed.distinctBy { it.id }.joinToString { sensorSummary(it) }
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
            serviceHealth.recordError("readSensor: ${e.message}")
            null
        }
    }

    fun checkSensorTimeouts(now: Long = System.currentTimeMillis()): List<TireSensor> {
        val lostSensors = mutableListOf<TireSensor>()
        val updated = _sensors.value.toMutableMap()
        var changed = false

        for ((id, sensor) in _sensors.value) {
            val seen = lastSeen[id] ?: continue
            if (now - seen > settingsStore.sensorTimeoutMs.value && sensor.alertType != AlertType.SENSOR_LOST) {
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

    fun closeUsb(clearSensorState: Boolean = true) {
        try {
            usbConnection.close()
            protocolRouter.onDongleClosed()
            dongleOpenedAtMs = 0L
            if (clearSensorState) {
                lastSeen.clear()
                lastFrameAtMs = 0L
                lastValidFrameAtMs = 0L
                _sensors.value = emptyMap()
                _state.value = TpmsState.Disconnected
                isReconnecting = false
            } else {
                isReconnecting = true
                _state.value = TpmsState.Connecting(attempt = 0)
            }
        } catch (e: Exception) {
            debugLog.warn("Repository", "closeUsb failed: ${e.message}")
            _state.value = if (clearSensorState) TpmsState.Disconnected else TpmsState.Connecting(attempt = 0)
        }
    }

    fun closeUsbForReconnect() {
        closeUsb(clearSensorState = false)
        serviceHealth.recordReconnect()
    }

    fun reconnectStaleFrame() {
        debugLog.warn("Repository", "Stale frame — forcing USB reconnect")
        closeUsbForReconnect()
        serviceHealth.recordStaleReconnect()
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
            } else {
                onSensorNormalCallback?.invoke(sensor.id)
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

            val alerting = sensorList.firstOrNull { it.isAlert }
            _state.value = if (alerting != null && alerting.alertType != null) {
                TpmsState.Alert(
                    sensor = alerting,
                    type = alerting.alertType,
                    previousState = TpmsState.Connected(sensorList, timestamp)
                )
            } else {
                TpmsState.Connected(sensorList, timestamp)
            }
        } catch (e: Exception) {
            debugLog.warn("Repository", "refreshConnectionState failed: ${e.message}")
        }
    }

    private fun emitAlert(sensor: TireSensor) {
        try {
            onAlertCallback?.invoke(sensor)
        } catch (e: Exception) {
            debugLog.warn("Repository", "emitAlert failed: ${e.message}")
        }
    }

    private fun sensorSummary(sensor: TireSensor): String = runCatching {
        val pressure = if (sensor.pressureKpa.isFinite()) "%.0f".format(sensor.pressureKpa) else "?"
        val temp = if (sensor.temperatureCelsius.isFinite()) "%.0f".format(sensor.temperatureCelsius) else "?"
        "${sensor.label} $pressure kPa ${temp}°C"
    }.getOrElse {
        debugLog.warn("Repository", "sensorSummary failed for ${sensor.id}: ${it.message}")
        "${sensor.label} (unreadable)"
    }

    private fun TireSensor.toReading() = SensorReading(
        sensorId = id,
        pressureKpa = pressureKpa,
        temperatureCelsius = temperatureCelsius,
        batteryPercent = batteryPercent,
        alertType = alertType?.name,
        timestamp = timestamp
    )
}
