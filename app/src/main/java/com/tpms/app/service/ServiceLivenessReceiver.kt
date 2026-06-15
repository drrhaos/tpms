package com.tpms.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tpms.app.di.UsbReceiverEntryPoint
import dagger.hilt.android.EntryPointAccessors

class ServiceLivenessReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ServiceLivenessScheduler.ACTION_CHECK) return

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            UsbReceiverEntryPoint::class.java
        )
        val debugLog = entryPoint.debugLog()
        val heartbeatStore = entryPoint.serviceHeartbeatStore()

        val running = ServiceRunningChecker.isRunning(context, TpmsMonitorService::class.java)
        val heartbeatStale = ServiceLivenessEvaluator.shouldRestart(heartbeatStore)

        if (!running || heartbeatStale) {
            debugLog.warn(
                TAG,
                "Liveness check failed (running=$running heartbeatStale=$heartbeatStale) — restarting service"
            )
            runCatching { TpmsMonitorService.start(context) }
            if (!running) {
                ServiceStoppedNotifier.show(context)
            }
        } else {
            Log.d(TAG, "Liveness check OK")
        }

        ServiceLivenessScheduler.schedule(context)
    }

    companion object {
        private const val TAG = "ServiceLiveness"
    }
}
