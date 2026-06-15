package com.tpms.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log

object WakeEscalationScheduler {

    const val ACTION_WAKE = "com.tpms.app.action.WAKE_ESCALATION"
    private const val REQUEST_CODE_BASE = 9100
    private val DELAYS_MS = longArrayOf(5_000L, 15_000L, 30_000L)
    private const val TAG = "WakeEscalation"

    fun onScreenOn(context: Context) {
        TpmsMonitorService.wake(context)
        DELAYS_MS.forEachIndexed { index, delayMs ->
            schedule(context, index, delayMs)
        }
    }

    private fun schedule(context: Context, index: Int, delayMs: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WakeEscalationReceiver::class.java).apply {
            action = ACTION_WAKE
            putExtra(EXTRA_INDEX, index)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_BASE + index,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val triggerAt = SystemClock.elapsedRealtime() + delayMs
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pending
                )
            } else {
                @Suppress("DEPRECATION")
                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending)
            }
            Log.d(TAG, "Scheduled wake escalation #${index + 1} in ${delayMs}ms")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to schedule wake escalation", e)
        }
    }

    const val EXTRA_INDEX = "wake_index"
}

class WakeEscalationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WakeEscalationScheduler.ACTION_WAKE) return
        TpmsMonitorService.wake(context)
    }
}
