package com.tpms.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import com.tpms.app.R
import com.tpms.app.TpmsApplication
import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.TireSensor
import com.tpms.app.ui.main.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val lastAlerted = mutableMapOf<String, AlertType>()

    fun notify(sensor: TireSensor) {
        try {
            val alertType = sensor.alertType ?: return
            if (lastAlerted[sensor.id] == alertType) return
            lastAlerted[sensor.id] = alertType

            val (title, body) = when (alertType) {
                AlertType.LOW_PRESSURE -> context.getString(R.string.notification_alert_low_pressure) to
                    "${sensor.id}: %.1f kPa".format(sensor.pressureKpa)
                AlertType.HIGH_PRESSURE -> context.getString(R.string.notification_alert_high_pressure) to
                    "${sensor.id}: %.1f kPa".format(sensor.pressureKpa)
                AlertType.HIGH_TEMP -> context.getString(R.string.notification_alert_high_temp) to
                    "${sensor.id}: %.0f°C".format(sensor.temperatureCelsius)
                AlertType.BATTERY_LOW -> context.getString(R.string.notification_alert_battery_low) to
                    "${sensor.id}: ${sensor.batteryPercent}%"
                AlertType.SENSOR_LOST -> context.getString(R.string.notification_alert_sensor_lost) to
                    sensor.id
            }

            val notification = NotificationCompat.Builder(context, TpmsApplication.CHANNEL_ALERT)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
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

    fun clear() {
        lastAlerted.clear()
    }
}
