package com.tpms.app.ui.main

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.min

/**
 * Main screen layout, readings first (drhaos, 2026-11-17: the screen is small, the car is only
 * decoration, older drivers must read the values at a glance). Pure, in dp.
 *
 * The four readings get the stage corners, each as large as the stage allows; the car is fitted into
 * what is left in the middle, so it can never cover a reading. Corners follow the render (nose to the
 * bottom-left): RR top-left, FR bottom-left, RL top-right, FL bottom-right — each the corner nearest
 * its wheel. Landscape: the readings form two side columns, the car sits between them; portrait: two
 * rows, the car between them. The spare, when shown, sits at the bottom centre of the middle band.
 */
internal data class MainStageLayout(
    val pressureSp: Float,
    val car: Rect,
    val readings: Map<String, Rect>,
    val spare: Rect?
)

/** A reading's width / height in pressure font sizes: "2.3 bar" over the wheel name and temperature. */
internal const val READING_WIDTH_EM = 4.6f
internal const val READING_HEIGHT_EM = 2.9f

/** The spare's reading, relative to a wheel's. */
internal const val SPARE_SCALE = 0.7f

private const val MAX_PRESSURE_SP = 80f
private const val MIN_PRESSURE_SP = 16f
private const val MARGIN = 4f
private const val GAP = 8f

/** Landscape: one side column of readings takes at most this share of the width. */
private const val COLUMN_SHARE = 0.30f

/** Portrait: one row of readings takes at most this share of the height. */
private const val ROW_SHARE = 0.25f

internal fun mainStageLayout(width: Float, height: Float, hasSpare: Boolean): MainStageLayout {
    val landscape = width >= height * 1.15f
    val pressure = if (landscape) {
        min((height - 2 * MARGIN - GAP) / (2 * READING_HEIGHT_EM), width * COLUMN_SHARE / READING_WIDTH_EM)
    } else {
        min((width - 2 * MARGIN - GAP) / (2 * READING_WIDTH_EM), height * ROW_SHARE / READING_HEIGHT_EM)
    }.coerceIn(MIN_PRESSURE_SP, MAX_PRESSURE_SP)
    val rw = pressure * READING_WIDTH_EM
    val rh = pressure * READING_HEIGHT_EM

    val left = MARGIN
    val right = width - MARGIN - rw
    val top = MARGIN
    val bottom = height - MARGIN - rh
    val readings = mapOf(
        "RR" to Rect(Offset(left, top), Size(rw, rh)),
        "FR" to Rect(Offset(left, bottom), Size(rw, rh)),
        "RL" to Rect(Offset(right, top), Size(rw, rh)),
        "FL" to Rect(Offset(right, bottom), Size(rw, rh))
    )

    // The middle band, between the columns (landscape) or the rows (portrait).
    var band = if (landscape) {
        Rect(left + rw + GAP, 0f, right - GAP, height)
    } else {
        Rect(0f, top + rh + GAP, width, bottom - GAP)
    }
    var spare: Rect? = null
    if (hasSpare) {
        val sw = (rw * SPARE_SCALE).coerceAtMost(band.width)
        val sh = rh * SPARE_SCALE
        spare = Rect(Offset(band.center.x - sw / 2f, band.bottom - sh), Size(sw, sh))
        band = Rect(band.left, band.top, band.right, band.bottom - sh)
    }

    // The car, fitted (contain) into the band.
    val bandW = band.width.coerceAtLeast(1f)
    val bandH = band.height.coerceAtLeast(1f)
    val carW = min(bandW, bandH * CAR_IMAGE_ASPECT)
    val carH = carW / CAR_IMAGE_ASPECT
    val car = Rect(Offset(band.center.x - carW / 2f, band.center.y - carH / 2f), Size(carW, carH))
    return MainStageLayout(pressure, car, readings, spare)
}
