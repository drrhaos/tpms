package com.tpms.app.data.diagnostics

/**
 * Pure Kotlin formatter for exception reports (unit-testable, no Android deps).
 */
object CrashReportFormatter {

    data class Options(
        val maxCauseDepth: Int = 5,
        val maxStackLines: Int = 12,
        val includeSuppressed: Boolean = true
    )

    fun formatThread(thread: Thread): String =
        "Thread: name=${thread.name}, id=${thread.id}, state=${thread.state}, priority=${thread.priority}"

    fun format(throwable: Throwable, options: Options = Options()): List<String> {
        val lines = mutableListOf<String>()
        var current: Throwable? = throwable
        var depth = 0

        while (current != null && depth < options.maxCauseDepth) {
            val label = if (depth == 0) "Exception" else "Caused by"
            lines += "$label: ${current.javaClass.name}: ${current.message ?: "(no message)"}"
            current.stackTrace
                .take(options.maxStackLines)
                .forEach { frame ->
                    lines += "  at $frame"
                }
            val hidden = current.stackTrace.size - options.maxStackLines
            if (hidden > 0) {
                lines += "  ... $hidden more frame(s)"
            }
            if (options.includeSuppressed) {
                current.suppressed.forEach { suppressed ->
                    lines += "Suppressed: ${suppressed.javaClass.name}: ${suppressed.message}"
                    suppressed.stackTrace.take(4).forEach { frame ->
                        lines += "  at $frame"
                    }
                }
            }
            current = current.cause
            depth++
        }

        if (current != null) {
            lines += "Caused by: (chain truncated at ${options.maxCauseDepth} levels)"
        }
        return lines
    }
}
