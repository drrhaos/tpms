package com.tpms.app.data.usb

import com.tpms.app.domain.model.TireSensor

/**
 * Accepts a new sensor value only after it arrives twice, unchanged, within [windowMs].
 * The receiver repeats each slot's cached value, so a real change confirms within one cycle,
 * while a corrupted frame that slipped past the checksum is never repeated.
 */
class ReadingConfirmationFilter(private val windowMs: Long = DEFAULT_WINDOW_MS) {

    private val confirmed = mutableMapOf<String, TireSensor>()
    private val pending = mutableMapOf<String, TireSensor>()

    /** Returns the reading once its value is confirmed, otherwise null. */
    fun offer(reading: TireSensor): TireSensor? {
        val current = confirmed[reading.id]
        if (current != null && sameValue(current, reading)) {
            pending.remove(reading.id)
            confirmed[reading.id] = reading
            return reading
        }

        val candidate = pending[reading.id]
        if (candidate != null &&
            sameValue(candidate, reading) &&
            reading.timestamp - candidate.timestamp <= windowMs
        ) {
            pending.remove(reading.id)
            confirmed[reading.id] = reading
            return reading
        }

        pending[reading.id] = reading
        return null
    }

    private fun sameValue(a: TireSensor, b: TireSensor): Boolean =
        a.label == b.label &&
            a.pressureKpa == b.pressureKpa &&
            a.temperatureCelsius == b.temperatureCelsius &&
            a.batteryPercent == b.batteryPercent &&
            a.flags == b.flags

    companion object {
        const val DEFAULT_WINDOW_MS = 5_000L
    }
}
