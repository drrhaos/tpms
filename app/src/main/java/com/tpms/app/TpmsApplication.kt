package com.tpms.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.res.Configuration
import com.tpms.app.data.diagnostics.SystemDiagnostics
import com.tpms.app.data.settings.AppLocale
import com.tpms.app.data.usb.UsbDebugLog
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TpmsApplication : Application() {

    @Inject lateinit var debugLog: UsbDebugLog
    @Inject lateinit var systemDiagnostics: SystemDiagnostics

    override fun onCreate() {
        super.onCreate()
        AppLocale.applyToResources(this)
        installGlobalExceptionHandler()
        systemDiagnostics.logStartup(debugLog)
        createNotificationChannels()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // A system configuration change resets the resources to the system language (below 13);
        // on 13+ a per-app language change lands here too: rename the channels.
        AppLocale.applyToResources(this)
        createNotificationChannels()
    }

    /** Channel names follow the app language: re-creating a channel only renames it. */
    fun refreshNotificationChannels() = createNotificationChannels()

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
        // Tyre alerts: heads-up without sound — the signal is played by AlertSoundPlayer, because a
        // channel's sound can stay silent without the app knowing (wayfinder alerts 04). A channel's
        // sound is fixed at creation, hence the new id; the old ones are deleted.
        LEGACY_ALERT_CHANNELS.forEach { manager.deleteNotificationChannel(it) }
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERT,
                getString(R.string.notification_channel_alert),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(true)
            }
        )
    }

    companion object {
        const val CHANNEL_STATUS = "tpms_status"
        const val CHANNEL_ALERT = "tpms_alerts_silent"
        /** Earlier alert channels (with their own sound); deleted on start. */
        private val LEGACY_ALERT_CHANNELS = listOf("tpms_alert", "tpms_alert_alarm")
    }
}
