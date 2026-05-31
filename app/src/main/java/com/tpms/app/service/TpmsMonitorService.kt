package com.tpms.app.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tpms.app.R
import com.tpms.app.TpmsApplication
import com.tpms.app.data.repository.TpmsRepository
import com.tpms.app.domain.model.TpmsState
import com.tpms.app.ui.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint
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
import javax.inject.Inject

@AndroidEntryPoint
class TpmsMonitorService : Service() {

    @Inject lateinit var repository: TpmsRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var muteUntil: Long = 0

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tpms:poll")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_MUTE -> {
                muteUntil = System.currentTimeMillis() + 30 * 60 * 1000
                return START_STICKY
            }
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        startForeground(NOTIF_ID, buildPersistentNotification())
        _isRunning.value = true
        startPolling()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        pollingJob?.cancel()
        pollingJob = null
        repository.closeUsb()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        _isRunning.value = false
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            repository.updateState(TpmsState.Disconnected)
            delay(500)

            while (isActive) {
                val dongle = repository.findDongle()
                if (dongle == null) {
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
                Log.d(TAG, "USB dongle opened: ${dongle.deviceName}")

                while (isActive && repository.findDongle() != null) {
                    val sensor = repository.readSensor()
                    if (sensor != null) {
                        repository.updateState(
                            TpmsState.Connected(
                                sensors = repository.sensors.value.values.toList(),
                                timestamp = sensor.timestamp
                            )
                        )
                    }
                    delay(POLL_INTERVAL_MS)
                }

                Log.d(TAG, "USB dongle detached, reconnecting...")
                repository.closeUsb()
                delay(RECONNECT_DEBOUNCE_MS)
            }
        }
    }

    private fun buildPersistentNotification() =
        NotificationCompat.Builder(this, TpmsApplication.CHANNEL_STATUS)
            .setContentTitle(getString(R.string.notification_service_running))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
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
                PendingIntent.getService(this, 1, Intent(this, TpmsMonitorService::class.java), PendingIntent.FLAG_IMMUTABLE)
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notification_action_mute),
                PendingIntent.getService(this, 2, Intent(this, TpmsMonitorService::class.java).apply { action = ACTION_MUTE }, PendingIntent.FLAG_IMMUTABLE)
            )
            .build()

    companion object {
        private const val TAG = "TpmsMonitorService"
        private const val NOTIF_ID = 1001
        private const val POLL_INTERVAL_MS = 2000L
        private const val USB_CHECK_INTERVAL_MS = 5000L
        private const val RECONNECT_DEBOUNCE_MS = 5000L

        const val ACTION_MUTE = "com.tpms.app.action.MUTE"
        const val ACTION_STOP = "com.tpms.app.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, TpmsMonitorService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TpmsMonitorService::class.java))
        }
    }
}
