package com.tpms.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tpms.app.di.UsbReceiverEntryPoint
import dagger.hilt.android.EntryPointAccessors

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != BootStartScheduler.ACTION_DELAYED_START) {
            return
        }

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            UsbReceiverEntryPoint::class.java
        )
        val debugLog = entryPoint.debugLog()

        debugLog.info(TAG, "Boot event: $action — starting TPMS service")
        runCatching {
            TpmsMonitorService.start(context)
        }.onSuccess {
            BootStartScheduler.cancel(context)
        }.onFailure { error ->
            Log.w(TAG, "Foreground service start failed on boot", error)
            debugLog.warn(TAG, "Boot start failed: ${error.message} — scheduling delayed retry")
            BootStartScheduler.scheduleDelayedStart(context)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
