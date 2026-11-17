package com.tpms.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tpms.app.di.AlertCoordinatorEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * Delete intent of the alert notifications: on Android 14+ an ongoing notification can be swiped,
 * but an alert must stay as long as it lasts (wayfinder alerts 05), so [AlertCoordinator] puts it
 * back at once, silently. Explicit intents only.
 */
class AlertNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ALERT_SWIPED) return
        val key = intent.getStringExtra(EXTRA_ALERT_KEY) ?: return
        runCatching {
            EntryPointAccessors.fromApplication(context.applicationContext, AlertCoordinatorEntryPoint::class.java)
                .alertCoordinator()
                .onNotificationSwiped(key)
        }.onFailure { Log.w(TAG, "Swipe not handled", it) }
    }

    companion object {
        private const val TAG = "AlertNotificationRcv"
        const val ACTION_ALERT_SWIPED = "com.tpms.app.action.ALERT_NOTIFICATION_SWIPED"
        const val EXTRA_ALERT_KEY = "com.tpms.app.extra.ALERT_KEY"
    }
}
