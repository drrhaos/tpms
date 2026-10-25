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
import com.tpms.app.data.usb.UsbConnection
import com.tpms.app.data.usb.UsbDebugLog
import com.tpms.app.data.usb.UsbDeviceInfo
import com.tpms.app.data.usb.protocol.Aa55AuxFrame
import com.tpms.app.data.usb.protocol.Aa55EmptySlots
import com.tpms.app.data.usb.toHexPreview
import com.tpms.app.data.settings.SettingsStore
import com.tpms.app.domain.AlertTracker
import com.tpms.app.domain.ConnectionHealthPolicy
import com.tpms.app.domain.RecoveryState
import com.tpms.app.domain.SensorFreshness
import com.tpms.app.domain.UsbFault
import com.tpms.app.domain.WheelAssignment
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
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

    /**
     * Stuck readers and silent sessions seen by this process; outlives a service instance.
     * Written only through [updateRecovery] / [decideRecovery] (atomic: loop thread and main thread race).
     */
    private val _recovery = MutableStateFlow(RecoveryState())
    val recovery: RecoveryState get() = _recovery.value

    /**
     * Last confirmed reading (alert applied) of every sensor that passed the decoder, by id, whether
     * or not it sits on a wheel: input of [WheelAssignment] and of the wheel-mapping UI / diagnostics.
     * Bounded by [WheelAssignment.retainSeen] (old sensors off the wheels and unmapped are dropped).
     * Written only under [wheelsLock], together with [wheels].
     */
    private val _seenSensors = MutableStateFlow<Map<String, TireSensor>>(emptyMap())
    val seenSensors = _seenSensors.asStateFlow()

    /**
     * Wheels: slot label → the seen sensor [WheelAssignment.assign] places there (wheel mapping, spare
     * toggle, AA55 empty slots), in slot order; empty slots are absent. The single published placement:
     * main screen, widgets, notification, overlay, settings and diagnostics read it, none re-derives it.
     * Only these sensors drive the state, alerts, history and the last-known snapshot (wayfinder 11).
     * Re-derived on every reading and on every change of its inputs.
     */
    private val _wheels = MutableStateFlow<Map<String, TireSensor>>(emptyMap())
    val wheels = _wheels.asStateFlow()

    /**
     * Wheel labels the AA55 receiver reported as empty in its answer (`09`) to the ID query `0x07`;
     * reset when a session opens (the query is re-sent on open).
     */
    private val _aa55EmptySlots = MutableStateFlow<Set<String>>(emptySet())
    val aa55EmptySlots = _aa55EmptySlots.asStateFlow()

    /** Alert history per sensor id (seen sensors only, see [recomputeWheelsLocked]): survives session reopen. */
    private val alertTracker = AlertTracker()

    /** Latest snapshot write (empty wheels = clear); conflated, written by one collector in order. */
    private val snapshotWrites = MutableStateFlow<SnapshotWrite?>(null)
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
     * Guards the seen sensors, [wheels], the state they produce and the alert callbacks computed from
     * them. Readings (reader thread), settings changes (IO) and the snapshot restore all re-place the
     * wheels under it and emit their alert / normal callbacks before releasing it, so notifications are
     * posted in the same order as the wheel changes they describe (the callbacks only post to the
     * service's main handler). The restore's "still empty" and "no session" checks hold while it
     * publishes. Never held across a suspension point.
     */
    private val wheelsLock = Any()

    init {
        repoScope.launch {
            settingsStore.awaitLoaded()
            restoreLastKnownIfNeeded()
        }
        repoScope.launch {
            // Mapping and spare toggle re-place the wheels at once, from the last readings.
            combine(settingsStore.wheelMapping, settingsStore.showSpareWheel) { _, _ -> }
                .collect { republishWheels("settings changed") }
        }
        repoScope.launch {
            snapshotWrites.filterNotNull().collect { write ->
                runCatching {
                    if (write.wheels.isEmpty()) {
                        lastKnownSnapshotStore.clear()
                    } else {
                        lastKnownSnapshotStore.save(write.wheels, write.protocol, write.savedAtMs)
                    }
                }.onFailure { debugLog.warn("Repository", "Snapshot write failed: ${it.message}") }
            }
        }
    }

    private suspend fun restoreLastKnownIfNeeded() {
        if (_seenSensors.value.isNotEmpty()) return
        val restored = lastKnownSnapshotStore.load() ?: return
        // Snapshots from older versions may hold sensors from corrupted frames (SENSOR_XX).
        val wheelLabels = WheelLayout.allSlots(showSpareWheel = true)
        val filtered = restored.filterValues { it.label.isBlank() || it.label in wheelLabels }
        if (filtered.isEmpty()) return
        val restoredProtocol = lastKnownSnapshotStore.protocol()
        val applied = synchronized(wheelsLock) {
            // Live frames may have filled the sensors while the snapshot was loading: they win.
            if (_seenSensors.value.isNotEmpty()) return@synchronized false
            // Flags before the sensors, so a reader never sees restored sensors without them.
            restoredFromSnapshot = true
            if (lastSessionProtocol == null) lastSessionProtocol = restoredProtocol
            if (lastLiveDataAtMs <= 0L) lastLiveDataAtMs = filtered.values.maxOf { it.timestamp }
            _seenSensors.value = filtered
            recomputeWheelsLocked()
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

    /** [callback] runs under [wheelsLock] on the repository's threads: it must not block (post and return). */
    fun onAlert(callback: (TireSensor) -> Unit) {
        onAlertCallback = callback
    }

    /** Same contract as [onAlert]: called under [wheelsLock], must not block. */
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
            hasData = _wheels.value.isNotEmpty(),
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
        wheels = _wheels.value,
        seenSensors = _seenSensors.value,
        aa55EmptySlots = _aa55EmptySlots.value,
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

    /** Atomically replaces the recovery state with [transform] of it; [transform] must be pure (it may be retried). */
    fun updateRecovery(transform: (RecoveryState) -> RecoveryState): RecoveryState =
        decideRecovery(transform) { it }

    /**
     * Atomically applies the pure [decide] to the current recovery state and stores [stateOf] its
     * result. [decide] is retried if another writer got in first, so the returned decision is always
     * the one computed from the state it replaced.
     */
    fun <D> decideRecovery(decide: (RecoveryState) -> D, stateOf: (D) -> RecoveryState): D {
        while (true) {
            val current = _recovery.value
            val decision = decide(current)
            val next = stateOf(decision)
            if (_recovery.compareAndSet(current, next)) {
                publishUsbFault()
                return decision
            }
        }
    }

    /**
     * Brings [usbFault] in line with the current recovery state. Loops until they agree, so a writer
     * that publishes after a newer recovery write still ends on the newest fault, not its own.
     */
    private fun publishUsbFault() {
        while (true) {
            val fault = _recovery.value.fault
            val published = _usbFault.value
            if (published == fault) return
            if (_usbFault.compareAndSet(published, fault)) {
                fault?.let { debugLog.warn("Repository", "USB fault: $it") }
            }
        }
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
            lastFrameAtMs = 0L
            lastValidFrameAtMs = 0L
            activeSession = session
            resetAa55EmptySlots()
            session.start()
            // Under the lock so a concurrent snapshot restore cannot overwrite Connecting with Disconnected
            // or the live protocol with the snapshot's.
            synchronized(wheelsLock) {
                lastSessionProtocol = protocol
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
            synchronized(wheelsLock) {
                alertTracker.clear()
                _seenSensors.value = emptyMap()
                _aa55EmptySlots.value = emptySet()
                _wheels.value = emptyMap()
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
            DecodeSettings(minLiveWheelPressureKpa = settingsStore.minLiveWheelPressureKpa.value)
        )
        result.auxFrames.forEach { logAuxFrame(it) }
        result.auxFrames.filterIsInstance<Aa55AuxFrame.SensorId>()
            .takeIf { it.isNotEmpty() }
            ?.let { applyAa55SlotIds(it) }
        if (result.sensors.isNotEmpty()) {
            // Intentional: any valid frame keeps the connection fresh, on a wheel or not (connection health, ticket 10).
            lastValidFrameAtMs = timestamp
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
            val onWheel = synchronized(wheelsLock) { placeReadingLocked(reading) } ?: return
            runCatching { sensorDao.insert(onWheel.toReading()) }
                .onFailure { debugLog.warn("Repository", "DB insert failed: ${it.message}") }
        } catch (e: Exception) {
            debugLog.warn("Repository", "applySensorUpdate failed: ${e.message}")
        }
    }

    /**
     * Records [reading] among the seen sensors, re-places the wheels and, if it sits on one, emits its
     * alert / normal callback, the state and a snapshot write. Returns the reading with its alert
     * applied when it is on a wheel, else null. Caller holds [wheelsLock].
     */
    private fun placeReadingLocked(reading: TireSensor): TireSensor? {
        // Memory for every seen sensor, so one placed later by a settings change shows its alert.
        val sensor = alertTracker.apply(reading, settingsStore.thresholds.value)
        _seenSensors.update { it + (sensor.id to sensor) }
        val before = recomputeWheelsLocked(now = sensor.timestamp)
        val after = _wheels.value
        val afterIds = after.values.mapTo(HashSet()) { it.id }
        before.values.map { it.id }.filter { it !in afterIds }.forEach { notifySensorNormal(it) }
        // A sensor off the wheels only waits for a mapping: no state, alert, history or snapshot.
        if (sensor.id !in afterIds) return null
        // Live data ends the snapshot state atomically with publishing the reading (see wheelsLock).
        restoredFromSnapshot = false
        lastLiveDataAtMs = sensor.timestamp
        if (sensor.isAlert) emitAlert(sensor) else notifySensorNormal(sensor.id)
        refreshConnectionStateLocked(sensor.timestamp)
        requestSnapshotWriteLocked()
        return sensor
    }

    /** Records which AA55 receiver slots are empty (`09` answers) and re-places the wheels if that changed. */
    private fun applyAa55SlotIds(frames: List<Aa55AuxFrame.SensorId>) {
        try {
            synchronized(wheelsLock) {
                val before = _aa55EmptySlots.value
                _aa55EmptySlots.value = Aa55EmptySlots.apply(before, frames)
                if (before != _aa55EmptySlots.value) {
                    republishWheelsLocked(
                        "AA55 empty slots: ${_aa55EmptySlots.value.sorted().ifEmpty { listOf("none") }.joinToString()}"
                    )
                }
            }
        } catch (e: Exception) {
            debugLog.warn("Repository", "applyAa55SlotIds failed: ${e.message}")
        }
    }

    /**
     * Forgets the previous session's empty slots (the ID query is re-sent on open). Readings of those
     * slots were the receiver's placeholder values, not sensors: they go too, so they cannot reach a
     * wheel before this session's `09` answers arrive.
     */
    private fun resetAa55EmptySlots() {
        synchronized(wheelsLock) {
            val stale = _aa55EmptySlots.value
            if (stale.isEmpty()) return
            _aa55EmptySlots.value = emptySet()
            _seenSensors.update { seen -> seen.filterValues { it.label !in stale } }
            republishWheelsLocked("AA55 empty slots reset")
        }
    }

    /**
     * Re-derives [wheels] from [seenSensors] and the current inputs and prunes the seen sensors (and
     * their alert memory) with [WheelAssignment.retainSeen]; returns the previous wheels.
     * Caller holds [wheelsLock].
     */
    private fun recomputeWheelsLocked(now: Long = System.currentTimeMillis()): Map<String, TireSensor> {
        val before = _wheels.value
        val mapping = settingsStore.wheelMapping.value
        val wheels = WheelAssignment.assign(
            seen = _seenSensors.value,
            wheelMapping = mapping,
            showSpareWheel = settingsStore.showSpareWheel.value,
            emptySlots = _aa55EmptySlots.value
        )
        val kept = WheelAssignment.retainSeen(_seenSensors.value, wheels, mapping, now)
        if (kept.size != _seenSensors.value.size) _seenSensors.value = kept
        alertTracker.retain(kept.keys)
        _wheels.value = wheels
        return before
    }

    /**
     * Re-places the wheels after a change of mapping or spare toggle, without waiting for a frame.
     */
    private fun republishWheels(reason: String) {
        try {
            synchronized(wheelsLock) { republishWheelsLocked(reason) }
        } catch (e: Exception) {
            debugLog.warn("Repository", "republishWheels failed: ${e.message}")
        }
    }

    /**
     * Re-places the wheels from the last readings: sensors that left a wheel clear their alert
     * notification, sensors that joined show their current alert, a live state is re-evaluated (also
     * when sensors only swapped slots) and the snapshot follows the wheels (cleared when none is left).
     * Caller holds [wheelsLock], so these effects are ordered with the reader's.
     */
    private fun republishWheelsLocked(reason: String) {
        val before = recomputeWheelsLocked()
        val after = _wheels.value
        if (before == after) return
        val beforeIds = before.values.mapTo(HashSet()) { it.id }
        val afterIds = after.values.mapTo(HashSet()) { it.id }
        (beforeIds - afterIds).forEach { notifySensorNormal(it) }
        after.values.filter { it.id !in beforeIds && it.isAlert }.forEach { emitAlert(it) }
        val state = _state.value
        if (state is TpmsState.Connected || state is TpmsState.Alert) {
            refreshConnectionStateLocked(System.currentTimeMillis())
        }
        requestSnapshotWriteLocked()
        debugLog.info(
            "Repository",
            "Wheels re-placed ($reason): ${after.map { (slot, sensor) -> "$slot=${sensor.id}" }.joinToString().ifEmpty { "none" }}"
        )
    }

    /** Publishes Connected / Alert from [wheels] (slot order). Caller holds [wheelsLock]. */
    private fun refreshConnectionStateLocked(timestamp: Long) {
        try {
            val sensorList = _wheels.value.values.toList()
            if (sensorList.isEmpty()) {
                // No wheel left (settings change): an alert must not outlive its sensor's wheel.
                if (_state.value is TpmsState.Alert) updateState(TpmsState.Connected(emptyList(), timestamp))
                return
            }

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

    /**
     * Queues a snapshot of the current wheels (sensors off the wheels never reach it); no wheel left
     * clears the stored snapshot. Saved with the time the data was last confirmed live, so a re-save
     * after a settings change does not make old data look fresh. Caller holds [wheelsLock], so the
     * latest request always reflects the latest wheels.
     */
    private fun requestSnapshotWriteLocked() {
        snapshotWrites.value = SnapshotWrite(
            wheels = _wheels.value.values.associateBy { it.id },
            protocol = lastSessionProtocol,
            savedAtMs = lastLiveDataAtMs.takeIf { it > 0L } ?: System.currentTimeMillis()
        )
    }

    private fun emitAlert(sensor: TireSensor) {
        try {
            onAlertCallback?.invoke(sensor)
        } catch (e: Exception) {
            debugLog.warn("Repository", "emitAlert failed: ${e.message}")
        }
    }

    private fun notifySensorNormal(sensorId: String) {
        try {
            onSensorNormalCallback?.invoke(sensorId)
        } catch (e: Exception) {
            debugLog.warn("Repository", "onSensorNormal failed: ${e.message}")
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

    /** One pending write of the last-known snapshot; empty [wheels] clears it. */
    private data class SnapshotWrite(
        val wheels: Map<String, TireSensor>,
        val protocol: DongleProtocol?,
        val savedAtMs: Long
    )

    companion object {
        private const val RAW_TAG = "TPMS_RAW"
        /** Upper bound on how long one consumer iteration waits for data. */
        private const val RECEIVE_TICK_MS = 1_000L
    }
}
