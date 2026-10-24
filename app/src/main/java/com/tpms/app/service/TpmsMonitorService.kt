package com.tpms.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tpms.app.R
import com.tpms.app.TpmsApplication
import com.tpms.app.data.diagnostics.ServiceHealth
import com.tpms.app.data.persistence.ServiceHeartbeatStore
import com.tpms.app.data.repository.TpmsRepository
import com.tpms.app.data.settings.SettingsStore
import com.tpms.app.data.usb.DongleSession
import com.tpms.app.data.usb.UsbDebugLog
import com.tpms.app.domain.RecoveryAction
import com.tpms.app.domain.RecoveryDecision
import com.tpms.app.domain.RecoveryState
import com.tpms.app.domain.SessionOutcome
import com.tpms.app.domain.SessionRecoveryPolicy
import com.tpms.app.domain.SuspendDetector
import com.tpms.app.domain.UsbFault
import com.tpms.app.domain.WakeAction
import com.tpms.app.domain.WakeSession
import com.tpms.app.domain.model.TpmsState
import com.tpms.app.ui.label
import com.tpms.app.ui.main.MainActivity
import com.tpms.app.ui.widget.TpmsWidget
import com.tpms.app.ui.widget.WidgetRemoteViews
import com.tpms.app.ui.widget.WidgetSnapshotBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@AndroidEntryPoint
class TpmsMonitorService : Service() {

    @Inject lateinit var repository: TpmsRepository
    @Inject lateinit var alertNotifier: AlertNotifier
    @Inject lateinit var monitoringHealthNotifier: MonitoringHealthNotifier
    @Inject lateinit var heartbeatStore: ServiceHeartbeatStore
    @Inject lateinit var settingsStore: SettingsStore
    @Inject lateinit var widgetSnapshotBuilder: WidgetSnapshotBuilder
    @Inject lateinit var serviceHealth: ServiceHealth
    @Inject lateinit var floatingOverlayController: FloatingOverlayController
    @Inject lateinit var debugLog: UsbDebugLog

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sessionJob: Job? = null
    private var uiJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var muteUntil: Long = 0
    private var intentionalStop = false
    private var foregroundRefused = false
    private var lastHeartbeatAtMs = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Wakes the dongle search early (CHECK_NOW, attach, permission granted). */
    private val searchWake = Channel<Unit>(Channel.CONFLATED)

    /** Forces an immediate UI/widget/notification refresh. */
    private val uiRefresh = Channel<Unit>(Channel.CONFLATED)

    /** Set by a wake; the session loop drops its search backoff back to the minimum. */
    private val searchIntervalReset = AtomicBoolean(false)

    private val suspendDetector = SuspendDetector({ SystemClock.elapsedRealtime() - SystemClock.uptimeMillis() })

    // SCREEN_ON/OFF are only delivered to runtime-registered receivers.
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> onScreenOn()
                Intent.ACTION_SCREEN_OFF -> {
                    suspendDetector.onScreenOff()
                    debugLog.info("Power", "SCREEN_OFF")
                }
            }
        }
    }
    private var screenReceiverRegistered = false

    // FYT ACC broadcasts come from other apps, so this one is exported. Logged only, for hardware checks.
    private val accReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            debugLog.info("Power", "ACC event (runtime): ${intent.action}")
        }
    }
    private var accReceiverRegistered = false

    override fun onCreate() {
        super.onCreate()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tpms:session").apply {
            setReferenceCounted(false)
        }

        repository.onAlert { sensor ->
            mainHandler.post {
                if (System.currentTimeMillis() < muteUntil) return@post
                alertNotifier.notify(sensor)
            }
        }
        repository.onSensorNormal { sensorId ->
            mainHandler.post { alertNotifier.clearSensor(sensorId) }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        reportPreviousRestart()
        screenReceiverRegistered = runCatching {
            ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }.isSuccess
        val accFilter = IntentFilter().apply { BootReceiver.ACC_ACTIONS.forEach { addAction(it) } }
        accReceiverRegistered = runCatching {
            ContextCompat.registerReceiver(this, accReceiver, accFilter, ContextCompat.RECEIVER_EXPORTED)
        }.isSuccess
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every start may come from startForegroundService(): promote before anything else.
        if (!promoteToForeground(buildPersistentNotification())) {
            foregroundRefused = true
            stopSelf(startId)
            return START_NOT_STICKY
        }
        foregroundRefused = false

        when (intent?.action) {
            ACTION_MUTE -> {
                muteUntil = System.currentTimeMillis() + MUTE_DURATION_MS
                return START_STICKY
            }
            ACTION_STOP -> {
                intentionalStop = true
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CHECK_NOW -> {
                if (intent.getBooleanExtra(EXTRA_DEVICE_ATTACHED, false)) onHeadUnitWake()
                searchWake.trySend(Unit)
                uiRefresh.trySend(Unit)
            }
            ACTION_PROBE_USB -> probeUsb(force = intent.getBooleanExtra(EXTRA_FORCE, false))
            ACTION_USB_DETACHED -> onUsbDetached(intent.getStringExtra(EXTRA_DEVICE_NAME))
            else -> {
                ServiceStoppedNotifier.dismiss(this)
                UsbPermissionNotifier.dismiss(this)
                alertNotifier.clear()
                monitoringHealthNotifier.clear()
                BootStartScheduler.cancel(this)
                ServiceLivenessScheduler.schedule(this)
            }
        }

        ensureMonitoring()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureMonitoring() {
        if (sessionJob?.isActive != true) startSessionLoop()
        if (uiJob?.isActive != true) startUiUpdates()
    }

    /** False when the system refuses a foreground start (API 31+ background-start restrictions). */
    private fun promoteToForeground(notification: Notification): Boolean = try {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                startForeground(
                    NOTIF_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                startForeground(
                    NOTIF_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            }
            else -> startForeground(NOTIF_ID, notification)
        }
        true
    } catch (e: IllegalStateException) {
        // ForegroundServiceStartNotAllowedException extends IllegalStateException.
        onForegroundRefused(e)
    } catch (e: SecurityException) {
        // API 34+: connectedDevice type prerequisites not met.
        onForegroundRefused(e)
    }

    private fun onForegroundRefused(error: Exception): Boolean {
        debugLog.error("Service", "startForeground refused: ${error.message}")
        serviceHealth.recordError("startForeground: ${error.message}")
        return false
    }

    override fun onDestroy() {
        sessionJob?.cancel()
        sessionJob = null
        uiJob?.cancel()
        uiJob = null
        repository.activeSession()?.let { session ->
            // Stopping the reader may wait on thread joins; keep it off the main thread.
            Thread({ repository.closeSession(session, "service stopped", keepSensors = false) }, "tpms-usb-close")
                .start()
        }
        if (screenReceiverRegistered) {
            runCatching { unregisterReceiver(screenReceiver) }
            screenReceiverRegistered = false
        }
        if (accReceiverRegistered) {
            runCatching { unregisterReceiver(accReceiver) }
            accReceiverRegistered = false
        }
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        if (!intentionalStop) {
            ServiceStoppedNotifier.show(this)
        }
        floatingOverlayController.hide(this)
        // After a refused foreground start the liveness alarm is what retries later.
        if (!foregroundRefused) ServiceLivenessScheduler.cancel(this)
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * Finds the dongle, opens a [DongleSession] and consumes it until it dies, then starts over.
     * The search interval grows while no dongle is found and resets once one opens or on a wake.
     */
    private fun startSessionLoop() {
        sessionJob?.cancel()
        sessionJob = serviceScope.launch {
            var searchIntervalMs = SEARCH_MIN_INTERVAL_MS
            while (isActive) {
                val dongle = repository.findDongle()
                val session = if (dongle != null && repository.hasUsbPermission(dongle)) {
                    repository.openSession(dongle) ?: run {
                        onOpenFailed()
                        null
                    }
                } else {
                    null
                }
                if (session == null) {
                    repository.updateState(TpmsState.Disconnected)
                    awaitSearchWake(searchIntervalMs)
                    searchIntervalMs = if (searchIntervalReset.getAndSet(false)) {
                        SEARCH_MIN_INTERVAL_MS
                    } else {
                        (searchIntervalMs * 2).coerceAtMost(SEARCH_MAX_INTERVAL_MS)
                    }
                    continue
                }
                searchIntervalMs = SEARCH_MIN_INTERVAL_MS
                searchIntervalReset.set(false)

                val reason = repository.consume(session) {
                    keepCpuAwake()
                    if (session.hasReceivedBytes) onBytes()
                }
                val stopped = repository.closeSession(session, reason)
                if (!stopped) serviceHealth.recordError("reader stuck after: $reason")
                val decision = SessionRecoveryPolicy.afterSession(
                    SessionOutcome(receivedBytes = session.hasReceivedBytes, readerStopped = stopped),
                    repository.recovery,
                    heartbeatStore.lastProcessRestartMs(),
                    System.currentTimeMillis()
                )
                when (val action = applyRecovery(decision)) {
                    RecoveryAction.RestartProcess -> restartProcess("reader stuck after: $reason")
                    is RecoveryAction.Reopen -> awaitSearchWake(action.delayMs)
                    null -> awaitSearchWake(SessionRecoveryPolicy.REOPEN_DELAY_MS)
                }
            }
        }
    }

    private suspend fun onOpenFailed() {
        val decision = SessionRecoveryPolicy.afterOpenFailed(
            repository.recovery,
            heartbeatStore.lastProcessRestartMs(),
            System.currentTimeMillis()
        )
        if (applyRecovery(decision) == RecoveryAction.RestartProcess) {
            restartProcess("open failed while a reader is stuck")
        }
    }

    private fun onBytes() {
        val current = repository.recovery
        val next = SessionRecoveryPolicy.onBytes(current)
        if (next != current) applyRecoveryState(next)
    }

    /** Dongle attach or USB permission granted: forget the silent streak and search at full speed. */
    private fun onHeadUnitWake() {
        applyRecoveryState(SessionRecoveryPolicy.onWake(repository.recovery))
        wakeSearch()
    }

    /**
     * Runtime SCREEN_ON (all devices): reset recovery, search at full speed, and reopen the session
     * if the device really suspended, or if a streaming session has gone quiet (soft probe).
     */
    private fun onScreenOn() {
        val suspended = suspendDetector.onScreenOn()
        val session = repository.activeSession()?.takeIf { !it.isClosed }
        val decision = SessionRecoveryPolicy.onWake(
            repository.recovery,
            suspended,
            session?.let { WakeSession(it.streamsContinuously, it.silentForMs()) }
        )
        debugLog.info("Power", "SCREEN_ON — suspended=$suspended action=${decision.action}")
        applyRecoveryState(decision.state)
        wakeSearch()
        if (decision.action == WakeAction.Reopen && session != null) {
            closeSessionAsync(session, if (suspended) "wake after suspend" else "wake: no data")
        }
    }

    private fun wakeSearch() {
        searchIntervalReset.set(true)
        searchWake.trySend(Unit)
    }

    private fun applyRecovery(decision: RecoveryDecision): RecoveryAction? {
        applyRecoveryState(decision.state)
        return decision.action
    }

    private fun applyRecoveryState(state: RecoveryState) {
        if (repository.updateRecovery(state)) {
            state.fault?.let { debugLog.warn("Service", "USB fault: $it") }
            uiRefresh.trySend(Unit)
        }
    }

    /**
     * Kills this process so a stuck reader thread and its connection go with it; START_STICKY
     * brings the service back (the liveness alarm is the fallback). The policy limits how often.
     */
    private suspend fun restartProcess(reason: String) {
        val saved = runCatching { heartbeatStore.recordProcessRestart(reason) }
        if (saved.isFailure) {
            // Without the saved time the 30 min limit is gone and kills could loop: show the fault instead.
            debugLog.error("Service", "Restart time not saved (${saved.exceptionOrNull()?.message}) — not restarting")
            applyRecoveryState(repository.recovery.copy(fault = UsbFault.USB_STUCK))
            return
        }
        debugLog.error("Service", "Restarting process: $reason")
        Process.killProcess(Process.myPid())
    }

    /** The in-memory diagnostics died with the old process; carry over why it was restarted. */
    private fun reportPreviousRestart() {
        serviceScope.launch {
            val atMs = heartbeatStore.lastProcessRestartMs() ?: return@launch
            if (System.currentTimeMillis() - atMs > RESTART_REPORT_WINDOW_MS) return@launch
            val reason = heartbeatStore.lastProcessRestartReason() ?: "unknown"
            debugLog.warn("Service", "Process was restarted ${(System.currentTimeMillis() - atMs) / 1000}s ago: $reason")
            serviceHealth.recordError("process restart: $reason")
        }
    }

    private suspend fun awaitSearchWake(timeoutMs: Long) {
        withTimeoutOrNull(timeoutMs) { searchWake.receive() }
    }

    /**
     * Reopens the session only if a continuously streaming dongle has gone quiet, so a probe
     * never tears down a healthy connection; [force] reopens unconditionally.
     */
    private fun probeUsb(force: Boolean) {
        val session = repository.activeSession()
        if (session == null || session.isClosed) {
            searchWake.trySend(Unit)
            return
        }
        val quiet = session.streamsContinuously && session.silentForMs() > SessionRecoveryPolicy.PROBE_QUIET_MS
        if (force || quiet) {
            closeSessionAsync(session, if (force) "forced reconnect" else "probe: no data")
        }
    }

    private fun onUsbDetached(deviceName: String?) {
        val session = repository.activeSession() ?: return
        if (deviceName == null || deviceName == session.deviceName) {
            closeSessionAsync(session, "detached")
        }
    }

    /** Makes the consumer loop end; the loop itself finishes the cleanup via closeSession(). */
    private fun closeSessionAsync(session: DongleSession, reason: String) {
        serviceScope.launch { session.close(reason) }
    }

    private fun keepCpuAwake() {
        wakeLock?.acquire(WAKE_LOCK_HOLD_MS)
    }

    private fun startUiUpdates() {
        uiJob?.cancel()
        uiJob = serviceScope.launch {
            val ticker = flow {
                while (true) {
                    emit(Unit)
                    delay(UI_TICK_MS)
                }
            }
            merge(
                repository.sensors.map { },
                repository.state.map { },
                ticker,
                uiRefresh.receiveAsFlow()
            )
                .conflate()
                .collect {
                    refreshOutputs()
                    delay(UI_MIN_INTERVAL_MS)
                }
        }
    }

    private suspend fun refreshOutputs() {
        runCatching { updateWidget() }
        runCatching { updateFloatingOverlay() }
        runCatching { evaluateMonitoringHealth() }
        recordHeartbeatIfDue()
        withContext(Dispatchers.Main) {
            runCatching { refreshPersistentNotification() }
        }
    }

    private suspend fun recordHeartbeatIfDue() {
        val now = System.currentTimeMillis()
        if (now - lastHeartbeatAtMs < HEARTBEAT_INTERVAL_MS) return
        lastHeartbeatAtMs = now
        runCatching { heartbeatStore.recordBeat() }
    }

    private fun refreshPersistentNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, buildPersistentNotification())
    }

    private fun evaluateMonitoringHealth() {
        if (repository.sensors.value.isEmpty() && !repository.isUsbConnected()) {
            monitoringHealthNotifier.clear()
            return
        }
        monitoringHealthNotifier.evaluate(
            disconnectedSinceMs = if (repository.state.value is TpmsState.Disconnected) {
                repository.disconnectedSinceMs()
            } else {
                null
            },
            dongleOpenedAtMs = repository.dongleOpenedAtMs(),
            lastValidFrameAtMs = repository.lastValidFrameAtMs()
        )
    }

    private suspend fun updateWidget() {
        val snapshot = widgetSnapshotBuilder.build(this)
        withContext(Dispatchers.Main) {
            runCatching { TpmsWidget.pushUpdate(this@TpmsMonitorService, snapshot) }
        }
    }

    private suspend fun updateFloatingOverlay() {
        if (!settingsStore.floatingOverlayEnabled.value) {
            withContext(Dispatchers.Main) { floatingOverlayController.hide(this@TpmsMonitorService) }
            return
        }
        val snapshot = widgetSnapshotBuilder.build(this)
        withContext(Dispatchers.Main) {
            floatingOverlayController.showOrUpdate(this@TpmsMonitorService, snapshot)
        }
    }

    private fun buildPersistentNotification(): Notification {
        val statusLine = repository.serviceStatusLine()
        val protocolUnhealthy = repository.isProtocolUnhealthy()
        val snapshot = widgetSnapshotBuilder.buildNow(this)
        val collapsed = WidgetRemoteViews.forNotificationCollapsed(this, snapshot)
        val expanded = WidgetRemoteViews.forNotificationExpanded(this, snapshot, statusLine)
        // Custom views hide the content text; the sub text stays visible in the notification header.
        val faultLabel = repository.usbFault.value?.label(this)
        val summary = buildString {
            append(WidgetRemoteViews.summaryLine(snapshot))
            faultLabel?.let { append(" · ").append(it) }
            if (protocolUnhealthy) {
                append(" · ")
                append(getString(R.string.notification_protocol_warn))
            }
        }

        return NotificationCompat.Builder(this, TpmsApplication.CHANNEL_STATUS)
            .setContentTitle(getString(R.string.notification_service_running))
            .setContentText(summary)
            .setSubText(faultLabel)
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setSmallIcon(
                if (protocolUnhealthy) android.R.drawable.ic_dialog_alert
                else android.R.drawable.ic_dialog_info
            )
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .addAction(
                android.R.drawable.ic_menu_search,
                getString(R.string.notification_action_check),
                PendingIntent.getService(
                    this, 1,
                    Intent(this, TpmsMonitorService::class.java).apply { action = ACTION_CHECK_NOW },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notification_action_mute),
                PendingIntent.getService(
                    this, 2,
                    Intent(this, TpmsMonitorService::class.java).apply { action = ACTION_MUTE },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()
    }

    companion object {
        private const val TAG = "TpmsMonitorService"
        private const val NOTIF_ID = 1001
        private const val SEARCH_MIN_INTERVAL_MS = 1_000L
        private const val SEARCH_MAX_INTERVAL_MS = 5_000L
        private const val RESTART_REPORT_WINDOW_MS = 10 * 60_000L
        private const val UI_MIN_INTERVAL_MS = 1_000L
        private const val UI_TICK_MS = 30_000L
        private const val HEARTBEAT_INTERVAL_MS = 10_000L
        private const val WAKE_LOCK_HOLD_MS = 5_000L
        private const val MUTE_DURATION_MS = 30 * 60 * 1000L

        const val ACTION_MUTE = "com.tpms.app.action.MUTE"
        const val ACTION_STOP = "com.tpms.app.action.STOP"
        const val ACTION_CHECK_NOW = "com.tpms.app.action.CHECK_NOW"
        const val ACTION_PROBE_USB = "com.tpms.app.action.PROBE_USB"
        const val ACTION_USB_DETACHED = "com.tpms.app.action.USB_DETACHED"
        const val EXTRA_FORCE = "com.tpms.app.extra.FORCE"
        const val EXTRA_DEVICE_NAME = "com.tpms.app.extra.DEVICE_NAME"
        const val EXTRA_DEVICE_ATTACHED = "com.tpms.app.extra.DEVICE_ATTACHED"

        fun start(context: Context) {
            startForegroundSafely(context, Intent(context, TpmsMonitorService::class.java))
        }

        /** Wake an already-running service: search for the dongle now and refresh outputs. */
        /** [deviceAttached] also resets the silent-dongle streak (see SessionRecoveryPolicy). */
        fun wake(context: Context, deviceAttached: Boolean = false) {
            val intent = Intent(context, TpmsMonitorService::class.java).apply {
                action = ACTION_CHECK_NOW
                putExtra(EXTRA_DEVICE_ATTACHED, deviceAttached)
            }
            startForegroundSafely(context, intent)
        }

        /** Reopen the dongle if it stopped sending data; [force] reopens it unconditionally. */
        fun probeUsb(context: Context, force: Boolean = false) {
            val intent = Intent(context, TpmsMonitorService::class.java).apply {
                action = ACTION_PROBE_USB
                putExtra(EXTRA_FORCE, force)
            }
            startForegroundSafely(context, intent)
        }

        /** Background starts can be refused on API 31+; the liveness alarm retries later. */
        private fun startForegroundSafely(context: Context, intent: Intent) {
            try {
                context.startForegroundService(intent)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Foreground start refused: ${e.message}")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TpmsMonitorService::class.java))
        }
    }
}
