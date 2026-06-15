package com.tpms.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import com.tpms.app.R
import com.tpms.app.TpmsApplication
import com.tpms.app.data.settings.SettingsStore
import com.tpms.app.domain.MonitoringHealthPolicy
import com.tpms.app.domain.WheelLayout
import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.TireSensor
import com.tpms.app.ui.main.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: SettingsStore
) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val lastAlerted = mutableMapOf<String, AlertType>()
    private val lastNotifiedAtMs = mutableMapOf<String, Long>()

    fun notify(sensor: TireSensor) {
        try {
            if (!NotificationHelper.canPostAlerts(context)) return

            val alertType = sensor.alertType ?: return
            val now = System.currentTimeMillis()
            val dedupeKey = "${sensor.id}:${alertType.name}"
            val isCritical = alertType == AlertType.LOW_PRESSURE ||
                alertType == AlertType.HIGH_PRESSURE ||
                alertType == AlertType.SENSOR_LOST

            if (lastAlerted[sensor.id] == alertType) {
                if (!isCritical) return
                val lastAt = lastNotifiedAtMs[dedupeKey] ?: return
                if (now - lastAt < MonitoringHealthPolicy.CRITICAL_ALERT_REPEAT_MS) return
            }

            lastAlerted[sensor.id] = alertType
            lastNotifiedAtMs[dedupeKey] = now

            val wheelLabel = WheelLayout.resolveWheelLabel(
                sensor,
                settingsStore.wheelMapping.value
            )
            val unit = settingsStore.pressureUnit.value
            val pressureText = when {
                alertType == AlertType.SENSOR_LOST -> null
                sensor.pressureKpa.isFinite() -> unit.formatPressure(sensor.pressureKpa)
                else -> "—"
            }

            val (title, body) = when (alertType) {
                AlertType.LOW_PRESSURE -> context.getString(R.string.notification_alert_low_pressure) to
                    "$wheelLabel: $pressureText"
                AlertType.HIGH_PRESSURE -> context.getString(R.string.notification_alert_high_pressure) to
                    "$wheelLabel: $pressureText"
                AlertType.HIGH_TEMP -> context.getString(R.string.notification_alert_high_temp) to
                    "$wheelLabel: %.0f°C".format(sensor.temperatureCelsius)
                AlertType.BATTERY_LOW -> context.getString(R.string.notification_alert_battery_low) to
                    "$wheelLabel: ${sensor.batteryPercent}%"
                AlertType.SENSOR_LOST -> context.getString(R.string.notification_alert_sensor_lost) to
                    wheelLabel
            }

            val alertPrefs = settingsStore.alertNotificationPrefs.value
            val defaults = when {
                alertPrefs.soundEnabled && alertPrefs.vibrationEnabled -> NotificationCompat.DEFAULT_ALL
                alertPrefs.soundEnabled -> NotificationCompat.DEFAULT_SOUND
                alertPrefs.vibrationEnabled -> NotificationCompat.DEFAULT_VIBRATE
                else -> 0
            }

            val notification = NotificationCompat.Builder(context, TpmsApplication.CHANNEL_ALERT)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(body)
                .setDefaults(defaults)
                .setSilent(!alertPrefs.soundEnabled && !alertPrefs.vibrationEnabled)
                .setPriority(
                    if (isCritical) NotificationCompat.PRIORITY_HIGH
                    else NotificationCompat.PRIORITY_DEFAULT
                )
                .setAutoCancel(true)
                .setContentIntent(
                    PendingIntent.getActivity(
                        context, sensor.id.hashCode(),
                        MainActivity.newIntent(context),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
                .build()

            notificationManager.notify(sensor.id.hashCode(), notification)
        } catch (_: Exception) {
            // Ignore bad notification payloads
        }
    }

    fun clearSensor(sensorId: String) {
        lastAlerted.remove(sensorId)
        lastNotifiedAtMs.keys.removeAll { it.startsWith("$sensorId:") }
    }

    fun clear() {
        lastAlerted.clear()
        lastNotifiedAtMs.clear()
    }
}
