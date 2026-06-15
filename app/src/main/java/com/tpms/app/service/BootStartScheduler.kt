package com.tpms.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log

object BootStartScheduler {

    const val ACTION_DELAYED_START = "com.tpms.app.action.DELAYED_BOOT_START"
    const val EXTRA_ATTEMPT = "boot_attempt"
    private const val REQUEST_CODE = 9001
    private const val TAG = "BootStartScheduler"
    private val RETRY_DELAYS_MS = longArrayOf(30_000L, 60_000L, 120_000L)

    fun scheduleDelayedStart(context: Context, attempt: Int = 0) {
        val delayMs = RETRY_DELAYS_MS.getOrElse(attempt) { RETRY_DELAYS_MS.last() }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, BootReceiver::class.java).apply {
            action = ACTION_DELAYED_START
            putExtra(EXTRA_ATTEMPT, attempt)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
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
            Log.d(TAG, "Scheduled delayed service start attempt=$attempt in ${delayMs}ms")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to schedule delayed start", e)
        }
    }

    fun cancel(context: Context) {
        val intent = Intent(context, BootReceiver::class.java).apply {
            action = ACTION_DELAYED_START
        }
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pending)
    }

    fun maxAttempts(): Int = RETRY_DELAYS_MS.size
}
