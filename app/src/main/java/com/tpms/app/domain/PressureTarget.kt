package com.tpms.app.domain

import com.tpms.app.domain.model.AlertThresholds
import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.PressureUnit
import com.tpms.app.domain.model.TireSensor
import kotlin.math.roundToInt

/**
 * The user sets one recommended (placard) pressure; the alert limits follow from it, as on OEM
 * systems (FMVSS 138 warns at 25 % below placard, BMW / Mercedes "accept current pressures").
 * Low = 80 % of the target, high = 135 % (hot tyres run ~10–15 % above cold).
 */
object PressureTarget {
    const val DEFAULT_KPA = 230f
    const val MIN_KPA = 150f
    const val MAX_KPA = 450f
    const val LOW_RATIO = 0.80f
    const val HIGH_RATIO = 1.35f

    /** "Accept current" rounds to 0.1 bar so the shown target is a clean number. */
    private const val CALIBRATION_STEP_KPA = 10f

    /** Wheels further apart than this cannot share one target (BMW refuses 0.4 bar per axle). */
    const val MAX_SPREAD_KPA = 50f

    /** Old builds stored this low limit by default; only a changed value says anything about the car. */
    private const val LEGACY_DEFAULT_LOW_KPA = 150f

    fun thresholds(targetKpa: Float, highTempCelsius: Float = AlertThresholds().highTempCelsius): AlertThresholds =
        AlertThresholds(
            lowPressureKpa = targetKpa * LOW_RATIO,
            highPressureKpa = targetKpa * HIGH_RATIO,
            highTempCelsius = highTempCelsius
        )

    fun clamp(kpa: Float): Float = kpa.coerceIn(MIN_KPA, MAX_KPA)

    /** Target for a user who only ever had the old absolute low limit (null = never stored). */
    fun fromLegacyLow(lowKpa: Float?): Float =
        if (lowKpa == null || lowKpa == LEGACY_DEFAULT_LOW_KPA) DEFAULT_KPA
        else clamp(roundTo(lowKpa / LOW_RATIO, CALIBRATION_STEP_KPA))

    /** Stepper step in [unit]: 0.1 bar, 1 psi, 5 kPa. */
    fun step(unit: PressureUnit): Float = when (unit) {
        PressureUnit.BAR -> 0.1f
        PressureUnit.PSI -> 1f
        PressureUnit.KPA -> 5f
    }

    /** One stepper click from [targetKpa] in [unit] (snapped to the unit's grid first), clamped. */
    fun stepped(targetKpa: Float, unit: PressureUnit, up: Boolean): Float {
        val step = step(unit)
        val snapped = (unit.fromKpa(targetKpa) / step).roundToInt()
        val next = (snapped + if (up) 1 else -1) * step
        return clamp(unit.toKpa(next))
    }

    sealed interface Calibration {
        data class Accepted(val targetKpa: Float) : Calibration
        /** A road wheel has no live reading (missing, lost or not a number). */
        data object MissingWheel : Calibration
        /** A wheel is below [MIN_KPA]: inflate first, a flat tyre must not become the norm. */
        data object TooLow : Calibration
        /** Wheels differ by more than [MAX_SPREAD_KPA]: one target would alarm on some of them. */
        data object Uneven : Calibration
    }

    /** "Accept current pressures as the norm" from the placed wheels (slot → sensor); the spare is ignored. */
    fun calibrate(wheels: Map<String, TireSensor>): Calibration {
        val pressures = WheelLayout.ORDER.map { slot ->
            val sensor = wheels[slot] ?: return Calibration.MissingWheel
            if (sensor.alertType == AlertType.SENSOR_LOST || !sensor.pressureKpa.isFinite()) {
                return Calibration.MissingWheel
            }
            sensor.pressureKpa
        }
        if (pressures.any { it < MIN_KPA }) return Calibration.TooLow
        if (pressures.max() - pressures.min() > MAX_SPREAD_KPA) return Calibration.Uneven
        return Calibration.Accepted(clamp(roundTo(pressures.average().toFloat(), CALIBRATION_STEP_KPA)))
    }

    private fun roundTo(value: Float, step: Float): Float = (value / step).roundToInt() * step
}
