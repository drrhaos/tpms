package com.tpms.app.data.repository

import android.hardware.usb.UsbDevice
import com.tpms.app.data.db.SensorDao
import com.tpms.app.data.db.SensorReading
import com.tpms.app.data.diagnostics.DiagnosticsExporter
import com.tpms.app.data.diagnostics.ServiceHealth
import com.tpms.app.data.diagnostics.UiBreadcrumbs
import com.tpms.app.data.persistence.LastKnownSnapshotStore
import com.tpms.app.data.usb.DecodeSettings
import com.tpms.app.data.usb.DongleDetector
import com.tpms.app.data.usb.DongleSession
import com.tpms.app.data.usb.FrameDecoder
import com.tpms.app.data.usb.SensorValidator
import com.tpms.app.data.usb.UsbConnection
import com.tpms.app.data.usb.UsbDebugLog
import com.tpms.app.data.usb.UsbDeviceInfo
import com.tpms.app.data.usb.protocol.Aa55AuxFrame
import com.tpms.app.data.usb.toHexPreview
import com.tpms.app.data.settings.SettingsStore
import com.tpms.app.domain.AlertTracker
import com.tpms.app.domain.ConnectionHealthPolicy
import com.tpms.app.domain.RecoveryState
import com.tpms.app.domain.SensorFreshness
import com.tpms.app.domain.UsbFault
import com.tpms.app.domain.WheelLayout
import com.tpms.app.domain.model.AlertThresholds
import com.tpms.app.domain.model.DongleProtocol
import com.tpms.app.domain.model.TireSensor
import com.tpms.app.domain.model.TpmsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TpmsRepository @Inject constructor(
    private val usbConnection: UsbConnection,
    private val dongleDetector: DongleDetector,
    private val sensorDao: SensorDao,
    private val settingsStore: SettingsStore,
    private val debugLog: UsbDebugLog,
    private val diagnosticsExporter: DiagnosticsExporter,
    private val uiBreadcrumbs: UiBreadcrumbs,
    private val serviceHealth: ServiceHealth,
    private val lastKnownSnapshotStore: LastKnownSnapshotStore
) {
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<TpmsState>(TpmsState.Disconnected)
    val state = _state.asStateFlow()

    /** USB problem reopening is not fixing, shown in the header and the persistent notification. */
    private val _usbFault = MutableStateFlow<UsbFault?>(null)
    val usbFault = _usbFault.asStateFlow()

    /** Stuck readers and silent sessions seen by this process; outlives a service instance. */
    @Volatile
    var recovery = RecoveryState()
        private set

    private val _sensors = MutableStateFlow<Map<String, TireSensor>>(emptyMap())
    val sensors = _sensors.asStateFlow()

    /** Alert history per sensor id: survives session reopen, dropped with the sensor. */
    private val alertTracker = AlertTracker()
    private var onAlertCallback: ((TireSensor) -> Unit)? = null
    private var onSensorNormalCallback: ((String) -> Unit)? = null

    @Volatile
    private var activeSession: DongleSession? = null

    @Volatile
    private var lastFrameAtMs: Long = 0L

    @Volatile
    private var lastValidFrameAtMs: Long = 0L

    @Volatile
    private var dongleOpenedAtMs: Long = 0L

    @Volatile
    private var isReconnecting: Boolean = false

    @Volatile
    private var disconnectedSinceMs: Long? = null

    @Volatile
    private var restoredFromSnapshot: Boolean = false

    /**
     * Protocol of the current or most recent session; kept after the session closes so HID wheels stay
     * stale-aware while reconnecting. Null until the first session of this process opens.
     */
    @Volatile
    private var lastSessionProtocol: DongleProtocol? = null

    /**
     * When the shown data was last confirmed by a valid frame; survives session close (unlike
     * [lastValidFrameAtMs]). After a snapshot restore it is the newest restored reading.
     */
    @Volatile
    private var lastLiveDataAtMs: Long = 0L

    /**
     * Serialises the snapshot restore against live data and session state: the restore's "still empty"
     * and "no session" checks must hold while it publishes the sensors, [restoredFromSnapshot] and the
     * Disconnected state. Never held across a suspension point.
     */
    private val restoreLock = Any()

    init {
        repoScope.launch {
            settingsStore.awaitLoaded()
            restoreLastKnownIfNeeded()
        }
        repoScope.launch {
            settingsStore.showSpareWheel.collect { showSpare ->
                if (!showSpare) {
                    removeSpareSensors()
                }
            }
        }
    }

    private suspend fun restoreLastKnownIfNeeded() {
        if (_sensors.value.isNotEmpty()) return
        val restored = lastKnownSnapshotStore.load() ?: return
        // Snapshots from older versions may hold sensors from corrupted frames (SENSOR_XX).
        val wheelLabels = WheelLayout.allSlots(showSpareWheel = true)
        val filtered = filterSensorsForSpareSetting(restored)
            .filterValues { it.label.isBlank() || it.label in wheelLabels }
        if (filtered.isEmpty()) return
        val restoredProtocol = lastKnownSnapshotStore.protocol()
        val applied = synchronized(restoreLock) {
            // Live frames may have filled the sensors while the snapshot was loading: they win.
            if (_sensors.value.isNotEmpty()) return@synchronized false
            // Flags before the sensors, so a reader never sees restored sensors without them.
            restoredFromSnapshot = true
            if (lastSessionProtocol == null) lastSessionProtocol = restoredProtocol
            if (lastLiveDataAtMs <= 0L) lastLiveDataAtMs = filtered.values.maxOf { it.timestamp }
            _sensors.value = filtered
            // A session opened (or reconnecting) meanwhile owns the state (Connecting); else Disconnected.
            if (activeSession == null && !isReconnecting) updateState(TpmsState.Disconnected)
            true
        }
        if (!applied) {
            debugLog.info("Repository", "Snapshot restore skipped: live data arrived first")
            return
        }
        debugLog.info(
            "Repository",
            "Restored ${filtered.size} sensors from last-known snapshot (${restoredProtocol?.displayName ?: "protocol unknown"})"
        )
    }

    fun onAlert(callback: (TireSensor) -> Unit) {
        onAlertCallback = callback
    }

    fun onSensorNormal(callback: (String) -> Unit) {
        onSensorNormalCallback = callback
    }

    fun findDongle(): UsbDevice? = usbConnection.findDongle(dongleDetector)

    fun isUsbConnected(): Boolean = activeSession?.isClosed == false

    fun isReconnecting(): Boolean = isReconnecting

    fun disconnectedSinceMs(): Long? = disconnectedSinceMs

    /** Inputs for the connection-based "data outdated" rule (wayfinder 10). */
    fun connectionSnapshot(): SensorFreshness.ConnectionSnapshot {
        val session = activeSession
        return SensorFreshness.ConnectionSnapshot(
            hasData = _sensors.value.isNotEmpty(),
            hasLiveSession = session != null && !session.isClosed && !isReconnecting && !restoredFromSnapshot,
            protocol = session?.protocol,
            sessionOpenedAtMs = dongleOpenedAtMs,
            lastValidFrameAtMs = lastValidFrameAtMs
        )
    }

    /**
     * "Data outdated" for the header and widget: judged by the connection; never true while no
     * sensor is shown (see [SensorFreshness.isDataStale]).
     */
    fun isDataStale(now: Long = System.currentTimeMillis()): Boolean =
        SensorFreshness.isDataStale(connectionSnapshot(), now)

    /**
     * Seconds since the shown data was last confirmed by a valid frame (any session), for the "N min"
     * next to "data outdated"; null when no data was ever received or restored.
     */
    fun dataAgeSec(now: Long = System.currentTimeMillis()): Long? {
        val at = lastLiveDataAtMs
        if (at <= 0L) return null
        return (now - at).coerceAtLeast(0L) / 1000
    }

    /** Protocol for per-wheel staleness ([SensorFreshness.isWheelStale]): live session, else the last one. */
    fun wheelFreshnessProtocol(): DongleProtocol? = activeSession?.protocol ?: lastSessionProtocol

    fun isProtocolUnhealthy(now: Long = System.currentTimeMillis()): Boolean =
        ConnectionHealthPolicy.isProtocolUnhealthy(
            dongleOpenedAtMs = dongleOpenedAtMs,
            lastValidFrameAtMs = lastValidFrameAtMs,
            now = now
        )

    fun shouldReconnectStaleFrame(now: Long = System.currentTimeMillis()): Boolean =
        ConnectionHealthPolicy.shouldReconnectStaleFrame(
            isUsbConnected = isUsbConnected(),
            dongleOpenedAtMs = dongleOpenedAtMs,
            lastValidFrameAtMs = lastValidFrameAtMs,
            staleThresholdMs = settingsStore.staleFrameTimeoutMs.value,
            lastBytesAtMs = lastFrameAtMs,
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
        appendLine("Open session: ${activeSession?.let { "${it.deviceName} (${it.vidPid})" } ?: "none"}")
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
        settingsUiMode = settingsStore.settingsUiMode.value,
        pressureUnit = settingsStore.pressureUnit.value,
        usbScan = scanUsbDevices(),
        serviceStatusLine = serviceStatusLine(),
        serviceHealth = serviceHealth
    )

    fun serviceStatusLine(): String {
        val protocol = activeProtocol()?.displayName ?: "no protocol"
        val dongle = activeSession?.vidPid ?: "no dongle"
        val frameAge = lastFrameAgeSeconds()?.let { "${it}s ago" } ?: "never"
        val validAge = lastValidFrameAgeSeconds()?.let { "${it}s ago" } ?: "never"
        val stateLabel = when (_state.value) {
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

    /** Stores [state] and publishes its fault; returns true if the shown fault changed. */
    fun updateRecovery(state: RecoveryState): Boolean {
        recovery = state
        if (_usbFault.value == state.fault) return false
        _usbFault.value = state.fault
        return true
    }

    fun lastFrameAtMs(): Long = lastFrameAtMs

    fun lastValidFrameAtMs(): Long = lastValidFrameAtMs

    fun dongleOpenedAtMs(): Long = dongleOpenedAtMs

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

    fun activeProtocol(): DongleProtocol? = activeSession?.protocol

    fun activeSession(): DongleSession? = activeSession

    /** Opens [device] and starts its reader. Returns null if it could not be opened. */
    fun openSession(device: UsbDevice): DongleSession? {
        return try {
            val protocol = dongleDetector.resolve(device, settingsStore.dongleProtocolMode.value)
            val link = usbConnection.open(device, protocol) ?: run {
                debugLog.error("Repository", "Failed to open dongle ${UsbDeviceInfo.vidPid(device)}")
                return null
            }
            val session = DongleSession(link, FrameDecoder(protocol), debugLog)
            dongleOpenedAtMs = session.openedAtMs
            lastSessionProtocol = protocol
            lastFrameAtMs = 0L
            lastValidFrameAtMs = 0L
            activeSession = session
            session.start()
            // Under the lock so a concurrent snapshot restore cannot overwrite Connecting with Disconnected.
            synchronized(restoreLock) {
                isReconnecting = false
                updateState(TpmsState.Connecting(attempt = 0))
            }
            debugLog.info("Repository", "Session opened: ${link.deviceName} (${link.vidPid}) as ${protocol.displayName}")
            session
        } catch (e: Exception) {
            debugLog.error("Repository", "openSession crashed: ${e.message}")
            serviceHealth.recordError("openSession: ${e.message}")
            null
        }
    }

    /**
     * Single consumer of [session]: decodes its chunks into sensor updates until the session dies.
     * Returns why it ended (reader stopped, silence, stale frames or device gone).
     */
    suspend fun consume(session: DongleSession, onIteration: () -> Unit = {}): String {
        var presenceCheckedAtMs = 0L
        while (true) {
            val chunk = session.receive(RECEIVE_TICK_MS)
            serviceHealth.recordPollStart()
            try {
                if (chunk != null) handleChunk(session, chunk)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                debugLog.warn("Repository", "handleChunk failed: ${e.message}")
                serviceHealth.recordError("handleChunk: ${e.message}")
            } finally {
                serviceHealth.recordPollCompleted()
            }
            onIteration()

            session.closeReason?.let { return it }
            if (session.streamsContinuously &&
                session.silentForMs() > settingsStore.silenceTimeoutMs.value
            ) {
                return "silence ${session.silentForMs() / 1000}s"
            }
            if (shouldReconnectStaleFrame()) {
                serviceHealth.recordStaleReconnect()
                return "no valid frame"
            }
            val now = System.currentTimeMillis()
            if (now - presenceCheckedAtMs >= RECEIVE_TICK_MS) {
                presenceCheckedAtMs = now
                if (!usbConnection.isDevicePresent(session.deviceName)) return "device gone"
            }
        }
    }

    /**
     * Closes [session]; with [keepSensors] the last readings stay visible while reconnecting.
     * Returns false if its reader thread could not be stopped.
     */
    fun closeSession(session: DongleSession, reason: String, keepSensors: Boolean = true): Boolean {
        debugLog.info("Repository", "Closing session ${session.deviceName}: $reason")
        val stopped = session.close(reason)
        if (activeSession === session) {
            activeSession = null
            dongleOpenedAtMs = 0L
            lastFrameAtMs = 0L
            lastValidFrameAtMs = 0L
        }
        if (keepSensors) {
            isReconnecting = true
            updateState(TpmsState.Connecting(attempt = 0))
            serviceHealth.recordReconnect()
        } else {
            alertTracker.clear()
            synchronized(restoreLock) {
                _sensors.value = emptyMap()
                restoredFromSnapshot = false
                lastLiveDataAtMs = 0L
                isReconnecting = false
                updateState(TpmsState.Disconnected)
            }
        }
        return stopped
    }

    fun updateState(state: TpmsState) {
        val wasDisconnected = _state.value is TpmsState.Disconnected
        val isDisconnected = state is TpmsState.Disconnected
        if (isDisconnected && !wasDisconnected) {
            disconnectedSinceMs = System.currentTimeMillis()
        } else if (!isDisconnected) {
            disconnectedSinceMs = null
        }
        _state.value = state
    }

    fun getThresholds(): AlertThresholds = settingsStore.thresholds.value

    fun recentHistory(limit: Int = 20): Flow<List<SensorReading>> =
        sensorDao.recentReadings(limit)

    private suspend fun handleChunk(session: DongleSession, raw: ByteArray) {
        val timestamp = System.currentTimeMillis()
        lastFrameAtMs = timestamp
        debugLog.raw(RAW_TAG, "${session.protocol.displayName} ${raw.toHexPreview()}")
        val result = session.decoder.decode(
            raw,
            timestamp,
            DecodeSettings(
                minLiveWheelPressureKpa = settingsStore.minLiveWheelPressureKpa.value,
                showSpareWheel = settingsStore.showSpareWheel.value
            )
        )
        result.auxFrames.forEach { logAuxFrame(it) }
        if (result.sensors.isNotEmpty()) {
            lastValidFrameAtMs = timestamp
            lastLiveDataAtMs = timestamp
            debugLog.info(
                "Repository",
                result.sensors.distinctBy { it.id }.joinToString { sensorSummary(it) }
            )
        }
        result.sensors.forEach { applySensorUpdate(it) }
    }

    private fun logAuxFrame(frame: Aa55AuxFrame) {
        when (frame) {
            is Aa55AuxFrame.SensorId ->
                debugLog.usb(RAW_TAG, "Sensor ID ${frame.slot}: ${frame.sensorId ?: "empty slot"}")
            is Aa55AuxFrame.Other ->
                debugLog.raw(RAW_TAG, "Receiver frame ${frame.kind}: ${frame.hex}")
        }
    }

    private suspend fun applySensorUpdate(reading: TireSensor) {
        try {
            if (!settingsStore.showSpareWheel.value && SensorValidator.isSpareSensor(reading)) {
                return
            }
            val sensor = alertTracker.apply(reading, settingsStore.thresholds.value)
            // Live data ends the snapshot state atomically with publishing the reading (see restoreLock).
            synchronized(restoreLock) {
                restoredFromSnapshot = false
                _sensors.update { it + (sensor.id to sensor) }
            }
            runCatching { sensorDao.insert(sensor.toReading()) }
                .onFailure { debugLog.warn("Repository", "DB insert failed: ${it.message}") }
            if (sensor.isAlert) {
                emitAlert(sensor)
            } else {
                onSensorNormalCallback?.invoke(sensor.id)
            }
            refreshConnectionState(sensor.timestamp)
            persistLastKnown()
        } catch (e: Exception) {
            debugLog.warn("Repository", "applySensorUpdate failed: ${e.message}")
        }
    }

    private fun refreshConnectionState(timestamp: Long) {
        try {
            val showSpareWheel = settingsStore.showSpareWheel.value
            val sensorList = _sensors.value.values
                .filter { showSpareWheel || !SensorValidator.isSpareSensor(it) }
                .toList()
            if (sensorList.isEmpty()) return

            val alerting = sensorList.firstOrNull { it.isAlert }
            val newState = if (alerting != null && alerting.alertType != null) {
                TpmsState.Alert(
                    sensor = alerting,
                    type = alerting.alertType,
                    previousState = TpmsState.Connected(sensorList, timestamp)
                )
            } else {
                TpmsState.Connected(sensorList, timestamp)
            }
            updateState(newState)
        } catch (e: Exception) {
            debugLog.warn("Repository", "refreshConnectionState failed: ${e.message}")
        }
    }

    private fun removeSpareSensors() {
        var removed = false
        _sensors.update { current ->
            val filtered = filterSensorsForSpareSetting(current)
            removed = filtered.size != current.size
            filtered
        }
        if (!removed) return
        val keep = _sensors.value.keys
        alertTracker.retain(keep)
        onSensorNormalCallback?.invoke(WheelLayout.SPARE_SLOT)
        refreshConnectionState(System.currentTimeMillis())
        persistLastKnown()
        debugLog.info("Repository", "Removed spare sensor(s); spare wheel display disabled")
    }

    private fun filterSensorsForSpareSetting(
        sensors: Map<String, TireSensor>
    ): Map<String, TireSensor> {
        if (settingsStore.showSpareWheel.value) return sensors
        return sensors.filterValues { !SensorValidator.isSpareSensor(it) }
    }

    private fun persistLastKnown() {
        val snapshot = filterSensorsForSpareSetting(_sensors.value)
        if (snapshot.isEmpty()) return
        val protocol = lastSessionProtocol
        repoScope.launch {
            runCatching { lastKnownSnapshotStore.save(snapshot, protocol) }
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

    companion object {
        private const val RAW_TAG = "TPMS_RAW"
        /** Upper bound on how long one consumer iteration waits for data. */
        private const val RECEIVE_TICK_MS = 1_000L
    }
}
