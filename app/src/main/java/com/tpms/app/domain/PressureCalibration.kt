package com.tpms.app.domain

import com.tpms.app.domain.model.PressureUnit
import com.tpms.app.domain.model.TireSensor
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Per-sensor pressure correction: the user compares a sensor with a reference gauge and sets an
 * offset that is added to every reading of that sensor. Keyed by sensor id, so the correction
 * follows the sensor through a tyre rotation. Applied to the reading before alerts, so limits,
 * notifications, "accept current", history and widgets all see the corrected pressure.
 */
object PressureCalibration {
    /** ±1 bar: a larger error means a wrong wheel or a broken sensor, not something to correct. */
    const val MAX_OFFSET_KPA = 100f

    fun clamp(offsetKpa: Float): Float = offsetKpa.coerceIn(-MAX_OFFSET_KPA, MAX_OFFSET_KPA)

    /** [reading] with [offsetKpa] added; the applied offset is kept so the raw value stays known. */
    fun apply(reading: TireSensor, offsetKpa: Float): TireSensor {
        if (offsetKpa == 0f && reading.pressureOffsetKpa == 0f) return reading
        val raw = reading.rawPressureKpa
        return reading.copy(
            pressureKpa = if (raw.isFinite()) raw + offsetKpa else raw,
            pressureOffsetKpa = offsetKpa
        )
    }

    /** Stepper step in [unit]: 0.01 bar, 0.1 psi, 1 kPa. */
    fun step(unit: PressureUnit): Float = when (unit) {
        PressureUnit.BAR -> 0.01f
        PressureUnit.PSI -> 0.1f
        PressureUnit.KPA -> 1f
    }

    /** One stepper click from [offsetKpa] in [unit] (snapped to the unit's grid first), clamped. */
    fun stepped(offsetKpa: Float, unit: PressureUnit, up: Boolean): Float {
        val step = step(unit)
        val snapped = (unit.fromKpa(offsetKpa) / step).roundToInt()
        val next = clamp(unit.toKpa((snapped + if (up) 1 else -1) * step))
        // A step back to the grid's zero must store exactly 0 (no correction), not a float residue.
        return if (abs(next) < unit.toKpa(step) / 2) 0f else next
    }

    /** A pressure in [unit] at the stepper's precision (the main screen rounds bar to 0.1). */
    fun format(kpa: Float, unit: PressureUnit): String = when (unit) {
        PressureUnit.BAR -> String.format(Locale.US, "%.2f", unit.fromKpa(kpa))
        PressureUnit.PSI -> String.format(Locale.US, "%.1f", unit.fromKpa(kpa))
        PressureUnit.KPA -> String.format(Locale.US, "%.0f", unit.fromKpa(kpa))
    }

    /** The offset with its sign ("+0.05", "−0.10", "0.00"). */
    fun formatOffset(offsetKpa: Float, unit: PressureUnit): String {
        val text = format(abs(offsetKpa), unit)
        return when {
            offsetKpa > 0f && text != format(0f, unit) -> "+$text"
            offsetKpa < 0f && text != format(0f, unit) -> "−$text"
            else -> text
        }
    }

    /** Stored / exported form: `{ "sensor id": offset kPa }`; zero offsets are left out. */
    fun encode(offsets: Map<String, Float>): String = JSONObject().apply {
        offsets.toSortedMap().forEach { (id, kpa) -> if (kpa != 0f) put(id, kpa.toDouble()) }
    }.toString()

    fun toJson(offsets: Map<String, Float>): JSONObject = JSONObject(encode(offsets))

    /** Reads [encode]'s form; unreadable input is no correction, bad entries are skipped, values clamped. */
    fun decode(json: String?): Map<String, Float> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching { fromJson(JSONObject(json)) }.getOrDefault(emptyMap())
    }

    fun fromJson(obj: JSONObject?): Map<String, Float> {
        if (obj == null) return emptyMap()
        return buildMap {
            obj.keys().forEach { id ->
                val kpa = obj.optDouble(id).toFloat()
                if (id.isNotBlank() && kpa.isFinite() && kpa != 0f) put(id, clamp(kpa))
            }
        }
    }
}
