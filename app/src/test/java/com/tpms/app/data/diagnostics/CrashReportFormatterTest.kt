package com.tpms.app.data.diagnostics

import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportFormatterTest {

    @Test
    fun format_includesExceptionClassAndStackFrame() {
        val error = RuntimeException("boom", IllegalStateException("root"))

        val lines = CrashReportFormatter.format(error)

        assertTrue(lines.any { it.startsWith("Exception: java.lang.RuntimeException: boom") })
        assertTrue(lines.any { it.startsWith("  at ") })
        assertTrue(lines.any { it.startsWith("Caused by: java.lang.IllegalStateException: root") })
    }

    @Test
    fun formatThread_containsThreadMetadata() {
        val line = CrashReportFormatter.formatThread(Thread.currentThread())

        assertTrue(line.contains("name="))
        assertTrue(line.contains("state="))
        assertTrue(line.contains("priority="))
    }

    @Test
    fun format_truncatesLongStacks() {
        val deep = DeepStackException(30)

        val lines = CrashReportFormatter.format(
            deep,
            CrashReportFormatter.Options(maxStackLines = 5)
        )

        assertTrue(lines.any { it.contains("25 more frame(s)") })
    }

    private class DeepStackException(depth: Int) : RuntimeException("depth=$depth") {
        init {
            if (depth > 0) {
                stackTrace = buildStackTrace(depth)
            }
        }

        private fun buildStackTrace(depth: Int): Array<StackTraceElement> =
            Array(depth) { index ->
                StackTraceElement("TestClass", "method$index", "Test.kt", index + 1)
            }
    }
}
