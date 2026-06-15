package com.tpms.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tpms.app.di.UsbReceiverEntryPoint
import dagger.hilt.android.EntryPointAccessors

class PowerEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            UsbReceiverEntryPoint::class.java
        )
        val debugLog = entryPoint.debugLog()

        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> {
                debugLog.info("Power", "SCREEN_ON — resuming USB monitor")
                TpmsMonitorService.wake(context)
            }
            Intent.ACTION_SCREEN_OFF -> {
                debugLog.info("Power", "SCREEN_OFF")
            }
        }
    }
}
