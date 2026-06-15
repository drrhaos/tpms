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

        val attempt = intent.getIntExtra(BootStartScheduler.EXTRA_ATTEMPT, 0)
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            UsbReceiverEntryPoint::class.java
        )
        val debugLog = entryPoint.debugLog()

        debugLog.info(TAG, "Boot event: $action attempt=$attempt — starting TPMS service")
        runCatching {
            TpmsMonitorService.start(context)
        }.onSuccess {
            BootStartScheduler.cancel(context)
        }.onFailure { error ->
            Log.w(TAG, "Foreground service start failed on boot", error)
            val nextAttempt = attempt + 1
            if (nextAttempt < BootStartScheduler.maxAttempts()) {
                debugLog.warn(
                    TAG,
                    "Boot start failed: ${error.message} — scheduling retry #$nextAttempt"
                )
                BootStartScheduler.scheduleDelayedStart(context, nextAttempt)
            } else {
                debugLog.error(TAG, "Boot start failed after ${BootStartScheduler.maxAttempts()} attempts")
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
