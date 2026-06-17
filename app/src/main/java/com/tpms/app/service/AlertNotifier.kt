package com.tpms.app.service

import android.app.NotificationManager
import android.content.Context
import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.TireSensor
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks active sensor alerts and dismisses legacy shade notifications.
 * In-app status is shown in the main screen header instead of posting alerts.
 */
@Singleton
class AlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val lastAlerted = mutableMapOf<String, AlertType>()

    fun notify(sensor: TireSensor) {
        val alertType = sensor.alertType ?: return
        lastAlerted[sensor.id] = alertType
    }

    fun clearSensor(sensorId: String) {
        lastAlerted.remove(sensorId)
        notificationManager.cancel(sensorId.hashCode())
    }

    fun clear() {
        lastAlerted.keys.forEach { notificationManager.cancel(it.hashCode()) }
        lastAlerted.clear()
    }
}
