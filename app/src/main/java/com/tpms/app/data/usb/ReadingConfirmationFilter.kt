package com.tpms.app.data.usb

import com.tpms.app.domain.model.TireSensor
import kotlin.math.abs

/**
 * Accepts a new sensor value only after it arrives twice within [windowMs], the second time within
 * the tolerance of the first ([pressureToleranceKpa], [temperatureToleranceC]; flags and label
 * always exact). The AA55 receiver repeats each slot's cached value, so there a real change confirms
 * within one cycle with zero tolerance, while a corrupted frame that slipped past the checksum is
 * never repeated. A HID dongle forwards each transmission once (about a minute apart when parked)
 * and a leaking tyre never sends the same value twice: [forHid] widens the window and allows one
 * step of drift, so a falling pressure still gets through.
 */
class ReadingConfirmationFilter(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val pressureToleranceKpa: Float = 0f,
    private val temperatureToleranceC: Float = 0f
) {

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
            abs(a.pressureKpa - b.pressureKpa) <= pressureToleranceKpa &&
            abs(a.temperatureCelsius - b.temperatureCelsius) <= temperatureToleranceC &&
            (pressureToleranceKpa > 0f || a.batteryPercent == b.batteryPercent) &&
            a.flags == b.flags

    companion object {
        const val DEFAULT_WINDOW_MS = 5_000L
        const val HID_WINDOW_MS = 3 * 60_000L

        /** One HID pressure step is 0.1 kPa; a leak may move a few kPa between two transmissions. */
        const val HID_PRESSURE_TOLERANCE_KPA = 7f
        const val HID_TEMPERATURE_TOLERANCE_C = 2f

        /** The HID variant: one transmission confirms the next if it is close enough (see the class doc). */
        fun forHid() = ReadingConfirmationFilter(HID_WINDOW_MS, HID_PRESSURE_TOLERANCE_KPA, HID_TEMPERATURE_TOLERANCE_C)
    }
}
