package com.tpms.app.domain

/**
 * Tells whether the device actually suspended between `SCREEN_OFF` and `SCREEN_ON`. A screen that
 * only went dark keeps the CPU running; a real suspend grows the time spent in deep sleep.
 *
 * [sleepClock] returns the total deep-sleep time so far, e.g.
 * `SystemClock.elapsedRealtime() - SystemClock.uptimeMillis()`.
 */
class SuspendDetector(
    private val sleepClock: () -> Long,
    private val thresholdMs: Long = SUSPEND_THRESHOLD_MS
) {
    private var sleepAtScreenOffMs: Long? = null

    /** Remembers the deep-sleep baseline; a later call replaces it. */
    fun onScreenOff() {
        sleepAtScreenOffMs = sleepClock()
    }

    /**
     * True if deep-sleep time grew by at least the threshold since [onScreenOff]; false if there
     * was no baseline. Consumes the baseline.
     */
    fun onScreenOn(): Boolean {
        val baseline = sleepAtScreenOffMs ?: return false
        sleepAtScreenOffMs = null
        return sleepClock() - baseline >= thresholdMs
    }

    companion object {
        const val SUSPEND_THRESHOLD_MS = 2_000L
    }
}
