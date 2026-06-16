package com.tpms.app.ui.settings

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.tpms.app.R
import com.tpms.app.startup.TeyesDeviceDetector

object TeyesPermissionHelper {

    private const val FRONTAPP_PACKAGE = "ru.fytmods.frontapp"
    private const val FYT_MS_PACKAGE = "com.syu.ms"

    private val TEYES_SETTINGS_PACKAGES = listOf(
        FYT_MS_PACKAGE,
        "com.teyes.settings",
        "com.android.settings"
    )

    fun openAppDetails(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        if (tryStart(context, intent)) return
        if (TeyesDeviceDetector.isLikelyTeyesHeadUnit(context) && openTeyesSettings(context)) return
        showManualHint(context, R.string.settings_teyes_manual_app)
    }

    fun openBatteryOptimization(context: Context) {
        if (TeyesDeviceDetector.isLikelyTeyesHeadUnit(context)) {
            if (openTeyesSettings(context)) return
        }
        val packageUri = Uri.parse("package:${context.packageName}")
        val requestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = packageUri
        }
        if (tryStart(context, requestIntent)) return
        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        if (tryStart(context, fallback)) return
        if (openTeyesSettings(context)) return
        showManualHint(context, R.string.settings_teyes_manual_battery)
    }

    fun openNotificationSettings(context: Context) {
        if (TeyesDeviceDetector.isLikelyTeyesHeadUnit(context) && openTeyesSettings(context)) return
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        }
        if (tryStart(context, intent)) return
        if (openTeyesSettings(context)) return
        showManualHint(context, R.string.settings_teyes_manual_notifications)
    }

    fun openOverlaySettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        if (tryStart(context, intent)) return
        if (openTeyesSettings(context)) return
        showManualHint(context, R.string.settings_teyes_manual_overlay)
    }

    /** Opens installed FrontApp or Play Store listing. */
    fun openFrontApp(context: Context): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(FRONTAPP_PACKAGE)
        if (launch != null && tryStart(context, launch)) return true
        return tryStart(
            context,
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$FRONTAPP_PACKAGE")
            )
        )
    }

    /** FYT/Teyes system settings (autostart, apps, general). */
    fun openTeyesSettings(context: Context): Boolean {
        val candidates = buildList {
            addAll(TEYES_SETTINGS_PACKAGES.mapNotNull { packageLaunchIntent(context, it) })
            add(componentIntent(FYT_MS_PACKAGE, "com.syu.ms.Settings"))
            add(componentIntent(FYT_MS_PACKAGE, "com.syu.ms.setting.MainActivity"))
            add(Intent(Settings.ACTION_SETTINGS))
        }
        return candidates.any { tryStart(context, it) }
    }

    private fun packageLaunchIntent(context: Context, packageName: String): Intent? =
        context.packageManager.getLaunchIntentForPackage(packageName)

    private fun componentIntent(packageName: String, className: String): Intent =
        Intent().setComponent(ComponentName(packageName, className))

    private fun tryStart(context: Context, intent: Intent): Boolean {
        val launchIntent = Intent(intent)
        if (context !is Activity) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (context.packageManager.resolveActivity(launchIntent, 0) == null) return false
        return runCatching {
            context.startActivity(launchIntent)
            true
        }.getOrDefault(false)
    }

    private fun showManualHint(context: Context, messageRes: Int) {
        Toast.makeText(context, context.getString(messageRes), Toast.LENGTH_LONG).show()
    }
}
