package com.tpms.app.data.usb

import com.tpms.app.data.diagnostics.CrashReportFormatter
import com.tpms.app.data.diagnostics.SystemDiagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsbDebugLog @Inject constructor(
    private val systemDiagnostics: SystemDiagnostics
) {

    enum class Level { INFO, WARN, ERROR, USB, RAW }

    data class Entry(
        val timestamp: Long,
        val level: Level,
        val tag: String,
        val message: String
    )

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun info(tag: String, message: String) = append(Level.INFO, tag, message)
    fun warn(tag: String, message: String) = append(Level.WARN, tag, message)
    fun error(tag: String, message: String) = append(Level.ERROR, tag, message)
    fun usb(tag: String, message: String) = append(Level.USB, tag, message)
    fun raw(tag: String, message: String) = append(Level.RAW, tag, message)

    fun exception(tag: String, throwable: Throwable, context: String? = null) {
        if (!context.isNullOrBlank()) {
            error(tag, context)
        }
        CrashReportFormatter.format(throwable).forEach { line ->
            append(Level.ERROR, tag, line)
        }
    }

    fun append(level: Level, tag: String, message: String) {
        val entry = Entry(System.currentTimeMillis(), level, tag, message)
        _entries.value = (_entries.value + entry).takeLast(MAX_ENTRIES)
    }

    fun clear() {
        _entries.value = emptyList()
    }

    fun exportText(): String = buildString {
        appendLine("=== TPMS Debug Log ===")
        append(systemDiagnostics.systemInfoBlock())
        appendLine("Exported: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        appendLine()
        for (entry in _entries.value) {
            appendLine("${timeFmt.format(Date(entry.timestamp))} [${entry.level}] ${entry.tag}: ${entry.message}")
        }
    }

    companion object {
        private const val MAX_ENTRIES = 500
    }
}
