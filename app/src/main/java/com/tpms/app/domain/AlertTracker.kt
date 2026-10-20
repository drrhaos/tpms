package com.tpms.app.domain

import com.tpms.app.domain.model.AlertThresholds
import com.tpms.app.domain.model.TireSensor

/**
 * Keeps each sensor's [AlertMemory] between readings so [AlertEvaluator] can apply hysteresis and
 * dwell timers. In-memory only; callers decide when a sensor's history ends.
 */
class AlertTracker {
    private val memories = HashMap<String, AlertMemory>()

    /** Evaluates [sensor] against its remembered history and returns it with the alert applied. */
    fun apply(sensor: TireSensor, thresholds: AlertThresholds): TireSensor = synchronized(this) {
        val memory = memories[sensor.id] ?: AlertMemory()
        val decision = AlertEvaluator.evaluate(sensor, thresholds, memory, sensor.timestamp)
        memories[sensor.id] = decision.memory
        sensor.copy(alertType = decision.alertType)
    }

    fun retain(ids: Set<String>): Unit = synchronized(this) { memories.keys.retainAll(ids) }

    fun clear(): Unit = synchronized(this) { memories.clear() }
}
