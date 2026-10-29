package com.tpms.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tpms.app.di.AlertNotifierEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * Delete intent of the per-wheel alert notifications (wayfinder 15): the user swiped one away, so
 * [AlertNotifier] keeps it hidden (persisted) until a type new to the wheel's alert episode appears.
 * Explicit intents only.
 */
class AlertNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ALERT_SWIPED) return
        val sensorId = intent.getStringExtra(EXTRA_SENSOR_ID) ?: return
        runCatching {
            EntryPointAccessors.fromApplication(context.applicationContext, AlertNotifierEntryPoint::class.java)
                .alertNotifier()
                .onSwiped(sensorId)
        }.onFailure { Log.w(TAG, "Swipe not applied", it) }
    }

    companion object {
        private const val TAG = "AlertNotificationRcv"
        const val ACTION_ALERT_SWIPED = "com.tpms.app.action.ALERT_NOTIFICATION_SWIPED"
        const val EXTRA_SENSOR_ID = "com.tpms.app.extra.SENSOR_ID"
    }
}
