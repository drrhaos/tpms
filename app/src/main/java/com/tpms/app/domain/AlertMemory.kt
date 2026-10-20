package com.tpms.app.domain

import com.tpms.app.domain.model.AlertType

/**
 * Per-sensor alert history that [AlertEvaluator] needs for hysteresis and dwell timers.
 * Each condition is tracked on its own, so a higher-priority alert never erases a lower one's state.
 */
data class AlertMemory(
    /** Threshold alert currently held by hysteresis (LOW_PRESSURE, HIGH_PRESSURE or HIGH_TEMP). */
    val thresholdAlert: AlertType? = null,
    /** Timestamp of the first reading in the current unbroken run of the leak flag. */
    val leakSinceMs: Long? = null,
    /** BATTERY_LOW is latched until the battery reads healthy for long enough. */
    val batteryLatched: Boolean = false,
    /** Timestamp of the first reading in the current unbroken run of healthy battery while latched. */
    val batteryOkSinceMs: Long? = null
)

data class AlertDecision(val alertType: AlertType?, val memory: AlertMemory)
