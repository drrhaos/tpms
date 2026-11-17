package com.tpms.app.domain

import com.tpms.app.domain.model.AlertLevel
import com.tpms.app.domain.model.AlertType

/**
 * Per-sensor alert history that [AlertEvaluator] needs for hysteresis, dwell timers and the rate of
 * pressure loss. Each condition is tracked on its own, so a higher-priority alert never erases a
 * lower one's state.
 */
data class AlertMemory(
    /** Pressure alert held by hysteresis (LOW_PRESSURE or HIGH_PRESSURE). */
    val pressureAlert: AlertType? = null,
    /** The held LOW_PRESSURE is critically low (FAULT), with its own hysteresis. */
    val pressureFault: Boolean = false,
    /** HIGH_TEMP held by hysteresis. */
    val tempAlert: Boolean = false,
    /** The held HIGH_TEMP is over the critical limit (FAULT). */
    val tempFault: Boolean = false,
    /** Timestamp of the first reading in the current unbroken run of the leak flag. */
    val leakSinceMs: Long? = null,
    /** BATTERY_LOW is latched until the battery reads healthy for long enough. */
    val batteryLatched: Boolean = false,
    /** Timestamp of the first reading in the current unbroken run of healthy battery while latched. */
    val batteryOkSinceMs: Long? = null,
    /** Recent pressures (oldest first, at most one per [AlertEvaluator.SAMPLE_EVERY_MS]) for the rate of loss. */
    val pressureSamples: List<PressureSample> = emptyList()
)

data class PressureSample(val atMs: Long, val kpa: Float)

data class AlertDecision(
    val alertType: AlertType?,
    val level: AlertLevel?,
    val memory: AlertMemory,
    /** Fall against the highest pressure of the window, when positive. */
    val recentDropKpa: Float? = null
)
