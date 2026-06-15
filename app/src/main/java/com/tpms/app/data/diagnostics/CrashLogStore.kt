package com.tpms.app.data.diagnostics

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrashLogStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val crashFile = File(context.filesDir, CRASH_FILE_NAME)

    fun save(report: String) {
        runCatching { crashFile.writeText(report) }
    }

    fun read(): String? = runCatching {
        if (crashFile.exists()) crashFile.readText() else null
    }.getOrNull()

    fun buildCrashReport(
        thread: Thread,
        throwable: Throwable,
        systemLines: List<String>,
        screenBreadcrumb: String
    ): String = buildString {
        appendLine("=== TPMS Crash Report ===")
        appendLine(CrashReportFormatter.formatThread(thread))
        appendLine(screenBreadcrumb)
        appendLine()
        CrashReportFormatter.format(
            throwable,
            CrashReportFormatter.Options(maxStackLines = 64, maxCauseDepth = 8)
        ).forEach { appendLine(it) }
        appendLine()
        appendLine("--- System at crash ---")
        systemLines.forEach { appendLine(it) }
    }

    companion object {
        private const val CRASH_FILE_NAME = "crash_last.txt"
    }
}
