package com.tpms.app.data.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.DisplayMetrics
import com.tpms.app.data.usb.UsbDebugLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemDiagnostics @Inject constructor(
    @ApplicationContext private val context: Context,
    private val uiBreadcrumbs: UiBreadcrumbs,
    private val crashLogStore: CrashLogStore
) {

    fun logStartup(debugLog: UsbDebugLog) {
        debugLog.info("System", "=== App startup ${timestamp()} ===")
        systemInfoLines().forEach { debugLog.info("System", it) }
    }

    fun logCrash(debugLog: UsbDebugLog, thread: Thread, throwable: Throwable) {
        val report = crashLogStore.buildCrashReport(
            thread = thread,
            throwable = throwable,
            systemLines = systemInfoLines(),
            screenBreadcrumb = uiBreadcrumbs.describe()
        )
        crashLogStore.save(report)
        debugLog.error("Crash", "=== UNCAUGHT EXCEPTION ${timestamp()} ===")
        debugLog.error("Crash", uiBreadcrumbs.describe())
        debugLog.error("Crash", CrashReportFormatter.formatThread(thread))
        CrashReportFormatter.format(
            throwable,
            CrashReportFormatter.Options(maxStackLines = 64, maxCauseDepth = 8)
        ).forEach { debugLog.error("Crash", it) }
        debugLog.error("Crash", "--- System snapshot at crash ---")
        systemInfoLines().forEach { debugLog.error("System", it) }
        debugLog.error("Crash", "Crash report persisted to crash_last.txt")
    }

    fun systemInfoBlock(): String = buildString {
        appendLine("--- System (${timestamp()}) ---")
        systemInfoLines().forEach { appendLine(it) }
        appendLine(uiBreadcrumbs.describe())
        appendLine()
    }

    fun systemInfoLines(): List<String> = runCatching {
        listOf(
            appInfo(),
            deviceInfo(),
            androidInfo(),
            displayInfo(),
            memoryInfo(),
            localeInfo(),
            hardwareFeatures()
        )
    }.getOrElse { listOf("System info unavailable: ${it.message}") }

    private fun appInfo(): String {
        val pm = context.packageManager
        val pkg = context.packageName
        val info = pm.getPackageInfo(pkg, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        return "App: $pkg v${info.versionName} ($versionCode)"
    }

    private fun deviceInfo(): String =
        "Device: ${Build.MANUFACTURER} ${Build.BRAND} ${Build.MODEL} " +
            "(device=${Build.DEVICE}, product=${Build.PRODUCT})"

    private fun androidInfo(): String {
        val patch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Build.VERSION.SECURITY_PATCH
        } else {
            "n/a"
        }
        return "Android: ${Build.VERSION.RELEASE} API ${Build.VERSION.SDK_INT}, " +
            "patch=$patch, fingerprint=${Build.FINGERPRINT}"
    }

    private fun displayInfo(): String {
        val metrics: DisplayMetrics = context.resources.displayMetrics
        val fontScale = context.resources.configuration.fontScale
        return "Display: ${metrics.widthPixels}x${metrics.heightPixels}px, " +
            "density=${metrics.densityDpi}dpi (${metrics.density}x), " +
            "fontScale=$fontScale"
    }

    private fun memoryInfo(): String {
        val runtime = Runtime.getRuntime()
        val heapUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / MB
        val heapTotalMb = runtime.totalMemory() / MB
        val heapMaxMb = runtime.maxMemory() / MB

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val availMb = memInfo.availMem / MB
        val totalMb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            memInfo.totalMem / MB
        } else {
            -1L
        }

        return buildString {
            append("Memory: heap ${heapUsedMb}/${heapTotalMb}MB max=${heapMaxMb}MB")
            append(", device avail=${availMb}MB")
            if (totalMb > 0) append(" total=${totalMb}MB")
            append(", lowMemory=${memInfo.lowMemory}")
        }
    }

    private fun localeInfo(): String {
        val locale = Locale.getDefault()
        return "Locale: ${locale.language}_${locale.country}, tz=${TimeZone.getDefault().id}"
    }

    private fun hardwareFeatures(): String {
        val pm = context.packageManager
        val usbHost = pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST)
        return "Hardware: ABIs=${Build.SUPPORTED_ABIS.joinToString()}, " +
            "board=${Build.BOARD}, hardware=${Build.HARDWARE}, USB host=$usbHost"
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    private companion object {
        const val MB = 1024L * 1024L
    }
}
