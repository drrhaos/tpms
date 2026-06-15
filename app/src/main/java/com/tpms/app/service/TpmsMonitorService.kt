package com.tpms.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import com.tpms.app.data.usb.UsbDeviceInfo
import androidx.core.app.NotificationCompat
import com.tpms.app.R
import com.tpms.app.TpmsApplication
import com.tpms.app.data.diagnostics.ServiceHealth
import com.tpms.app.data.persistence.ServiceHeartbeatStore
import com.tpms.app.data.repository.TpmsRepository
import com.tpms.app.data.settings.SettingsStore
import com.tpms.app.data.usb.UsbConnection
import com.tpms.app.domain.ConnectionHealthPolicy
import com.tpms.app.domain.model.TpmsState
import com.tpms.app.ui.main.MainActivity
import com.tpms.app.ui.widget.TpmsWidget
import com.tpms.app.ui.widget.WidgetRemoteViews
import com.tpms.app.ui.widget.WidgetSnapshot
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class TpmsMonitorService : Service() {

    @Inject lateinit var repository: TpmsRepository
    @Inject lateinit var alertNotifier: AlertNotifier
    @Inject lateinit var monitoringHealthNotifier: MonitoringHealthNotifier
    @Inject lateinit var heartbeatStore: ServiceHeartbeatStore
    @Inject lateinit var settingsStore: SettingsStore
    @Inject lateinit var serviceHealth: ServiceHealth

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null
    private var watchdogJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var muteUntil: Long = 0
    private var intentionalStop = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tpms:poll")

        repository.onAlert { sensor ->
            mainHandler.post {
                if (System.currentTimeMillis() < muteUntil) return@post
                alertNotifier.notify(sensor)
            }
        }
        repository.onSensorNormal { sensorId ->
            mainHandler.post { alertNotifier.clearSensor(sensorId) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
                serviceScope.launch { pollOnce() }
                return START_STICKY
            }
            ACTION_USB_DETACHED -> {
                serviceScope.launch {
                    runCatching { repository.closeUsbForReconnect() }
                }
                return START_STICKY
            }
        }

        startForeground(NOTIF_ID, buildPersistentNotification())
        ServiceStoppedNotifier.dismiss(this)
        UsbPermissionNotifier.dismiss(this)
        BootStartScheduler.cancel(this)
        ServiceLivenessScheduler.schedule(this)
        _isRunning.value = true
        if (pollingJob?.isActive != true) {
            startPolling()
        }
        if (watchdogJob?.isActive != true) {
            startWatchdog()
        }
        refreshPersistentNotification()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        pollingJob?.cancel()
        pollingJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        repository.closeUsb()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        _isRunning.value = false
        if (!intentionalStop) {
            ServiceStoppedNotifier.show(this)
        }
        ServiceLivenessScheduler.cancel(this)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            if (!repository.isUsbConnected()) {
                repository.updateState(TpmsState.Disconnected)
                delay(500)
            }

            while (isActive) {
                val dongle = repository.findDongle()
                if (dongle == null) {
                    repository.updateState(TpmsState.Disconnected)
                    delay(USB_CHECK_INTERVAL_MS)
                    continue
                }

                if (!repository.hasUsbPermission(dongle)) {
                    repository.updateState(TpmsState.Disconnected)
                    delay(USB_CHECK_INTERVAL_MS)
                    continue
                }

                if (!repository.openDongle(dongle)) {
                    repository.updateState(TpmsState.Disconnected)
                    delay(USB_CHECK_INTERVAL_MS)
                    continue
                }

                repository.updateState(TpmsState.Connecting(attempt = 0))
                Log.d(TAG, "USB dongle opened: ${dongle.deviceName} (${UsbDeviceInfo.vidPid(dongle)})")

                var absentPolls = 0
                while (isActive && repository.isUsbConnected()) {
                    try {
                        if (repository.shouldReconnectStaleFrame()) {
                            Log.w(TAG, "Stale frame detected — reconnecting USB")
                            repository.reconnectStaleFrame()
                            break
                        }

                        pollOnce()

                        val present = repository.isOpenDevicePresent()
                        if (present) {
                            absentPolls = 0
                        } else {
                            absentPolls++
                            if (absentPolls >= DETACH_ABSENT_POLLS) {
                                if (repository.probeUsbConnection()) {
                                    absentPolls = DETACH_ABSENT_POLLS - 1
                                } else {
                                    Log.d(TAG, "Dongle lost (absent from USB list, probe failed)")
                                    break
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Poll iteration failed", e)
                        serviceHealth.recordError("poll: ${e.message}")
                    }
                    delay(POLL_INTERVAL_MS)
                }

                Log.d(TAG, "USB dongle detached, reconnecting...")
                runCatching { repository.closeUsbForReconnect() }
                delay(RECONNECT_DEBOUNCE_MS)
            }
        }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                val lastCompleted = repository.lastPollCompletedAtMs()
                if (lastCompleted <= 0L) continue
                val stuckFor = System.currentTimeMillis() - lastCompleted
                if (stuckFor > ConnectionHealthPolicy.POLL_STUCK_MS) {
                    Log.w(TAG, "Watchdog: polling stuck for ${stuckFor}ms — restarting loop")
                    serviceHealth.recordWatchdogRestart()
                    pollingJob?.cancel()
                    pollingJob = null
                    startPolling()
                }
            }
        }
    }

    private suspend fun pollOnce() {
        serviceHealth.recordPollStart()
        acquireWakeLock()
        try {
            runCatching { repository.readSensor() }
            runCatching { repository.checkSensorTimeouts() }
            runCatching { updateWidget() }
            runCatching { evaluateMonitoringHealth() }
            serviceScope.launch { heartbeatStore.recordBeat() }
            withContext(Dispatchers.Main) {
                refreshPersistentNotification()
            }
        } finally {
            serviceHealth.recordPollCompleted()
            releaseWakeLock()
        }
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
        val snapshot = buildWidgetSnapshot()
        withContext(Dispatchers.Main) {
            runCatching { TpmsWidget.pushUpdate(this@TpmsMonitorService, snapshot) }
        }
    }

    private fun acquireWakeLock() {
        wakeLock?.acquire(UsbConnection.READ_TIMEOUT_MS + 500)
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
    }

    private fun buildWidgetSnapshot(): WidgetSnapshot =
        WidgetSnapshot.from(
            this,
            state = repository.state.value,
            sensors = repository.sensors.value,
            unit = settingsStore.pressureUnit.value,
            wheelMapping = settingsStore.wheelMapping.value,
            showSpareWheel = settingsStore.showSpareWheel.value,
            dataAgeSec = repository.newestSensorAgeSec(),
            dataStale = repository.isDataStale()
        )

    private fun buildPersistentNotification(): android.app.Notification {
        val statusLine = repository.serviceStatusLine()
        val protocolUnhealthy = repository.isProtocolUnhealthy()
        val snapshot = buildWidgetSnapshot()
        val collapsed = WidgetRemoteViews.forNotificationCollapsed(this, snapshot)
        val expanded = WidgetRemoteViews.forNotificationExpanded(this, snapshot, statusLine)
        val summary = buildString {
            append(WidgetRemoteViews.summaryLine(snapshot))
            if (protocolUnhealthy) {
                append(" · ")
                append(getString(R.string.notification_protocol_warn))
            }
        }

        return NotificationCompat.Builder(this, TpmsApplication.CHANNEL_STATUS)
            .setContentTitle(getString(R.string.notification_service_running))
            .setContentText(summary)
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
        private const val POLL_INTERVAL_MS = 2000L
        private const val USB_CHECK_INTERVAL_MS = 5000L
        private const val RECONNECT_DEBOUNCE_MS = 5000L
        private const val DETACH_ABSENT_POLLS = 3
        private const val MUTE_DURATION_MS = 30 * 60 * 1000L
        private const val WATCHDOG_INTERVAL_MS = 5000L

        const val ACTION_MUTE = "com.tpms.app.action.MUTE"
        const val ACTION_STOP = "com.tpms.app.action.STOP"
        const val ACTION_CHECK_NOW = "com.tpms.app.action.CHECK_NOW"
        const val ACTION_USB_DETACHED = "com.tpms.app.action.USB_DETACHED"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, TpmsMonitorService::class.java))
        }

        /** Wake an already-running service without restarting the USB polling loop. */
        fun wake(context: Context) {
            val intent = Intent(context, TpmsMonitorService::class.java).apply {
                action = ACTION_CHECK_NOW
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TpmsMonitorService::class.java))
        }
    }
}
