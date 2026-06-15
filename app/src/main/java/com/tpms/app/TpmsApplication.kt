package com.tpms.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.tpms.app.data.diagnostics.SystemDiagnostics
import com.tpms.app.data.usb.UsbDebugLog
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TpmsApplication : Application() {

    @Inject lateinit var debugLog: UsbDebugLog
    @Inject lateinit var systemDiagnostics: SystemDiagnostics

    override fun onCreate() {
        super.onCreate()
        installGlobalExceptionHandler()
        systemDiagnostics.logStartup(debugLog)
        createNotificationChannels()
    }

    private fun installGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (::debugLog.isInitialized && ::systemDiagnostics.isInitialized) {
                systemDiagnostics.logCrash(debugLog, thread, throwable)
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATUS,
                getString(R.string.notification_channel_status),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERT,
                getString(R.string.notification_channel_alert),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(false)
                setShowBadge(true)
            }
        )
    }

    companion object {
        const val CHANNEL_STATUS = "tpms_status"
        const val CHANNEL_ALERT = "tpms_alert"
    }
}
