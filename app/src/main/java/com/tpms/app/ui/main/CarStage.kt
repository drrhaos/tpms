package com.tpms.app.ui.main

import androidx.annotation.DrawableRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.tpms.app.R
import kotlin.math.min

// Geometry of the x-ray car renders (tools/car_render), shared by the main screen and the
// home-screen widget so both show the same scene.

/** Width / height of `car_3d_*` and `car_3d_wheel_*` (Blender x-ray render, nose to the bottom-left). */
internal const val CAR_IMAGE_ASPECT = 1349f / 1091f

/** The car takes this share of the stage: room for the road around it and for large readings. */
internal const val CAR_SCALE = 0.8f

/**
 * `car_3d_env_*` (road, lane lines, floor grid, light pool; same camera, wider frame) in fractions
 * of the car image: it lines up with the car when scaled by the car's size.
 */
internal val ENV_RECT = Rect(Offset(-1.0756f, -0.6682f), Size(3.1134f, 2.0532f))

/**
 * Each wheel: its tint mask (tyre + rim, rendered with the same camera, shaded so the spokes stay
 * readable) and where its reading sits, as a fraction of the car image: beside its own wheel,
 * outside the body, on its axle's line extended past the wheel (hubs FL 0.438,0.721 / FR 0.175,0.563
 * / RL 0.737,0.340 / RR 0.494,0.222), 0.27 of the image width beyond the hub, like BMW's tyre page.
 */
internal data class WheelSpot(val slot: String, @DrawableRes val mask: Int, val reading: Offset)

internal val WHEEL_SPOTS = listOf(
    WheelSpot("FR", R.drawable.car_3d_wheel_fr, Offset(-0.095f, 0.402f)),
    WheelSpot("RR", R.drawable.car_3d_wheel_rr, Offset(0.224f, 0.091f)),
    WheelSpot("RL", R.drawable.car_3d_wheel_rl, Offset(1.007f, 0.471f)),
    WheelSpot("FL", R.drawable.car_3d_wheel_fl, Offset(0.708f, 0.882f))
)

/** Wheel hubs as fractions of the car image (post.py output): where the swap arrows start and end. */
internal val WHEEL_HUBS = mapOf(
    "FL" to Offset(0.438f, 0.721f),
    "FR" to Offset(0.175f, 0.563f),
    "RL" to Offset(0.737f, 0.340f),
    "RR" to Offset(0.494f, 0.222f)
)

/** Stage layout in dp: the car's rect, the reading size; shared by [CarTopDown] and the widget. */
internal data class StageGeometry(val compact: Boolean, val pressureSp: Float, val car: Rect, val readingW: Float, val readingH: Float)

/** [carScale]: the widget gives the car more of its small stage than the main screen does. */
internal fun stageGeometry(width: Float, height: Float, compact: Boolean, carScale: Float = CAR_SCALE): StageGeometry {
    val pad = if (compact) 4f else 8f
    val pressure = (height * 0.10f).coerceIn(if (compact) 20f else 30f, if (compact) 30f else 52f)
    val carW = (min(width - 2 * pad, (height - 2 * pad) * CAR_IMAGE_ASPECT) * carScale).coerceAtLeast(1f)
    val carH = carW / CAR_IMAGE_ASPECT
    val car = Rect(Offset((width - carW) / 2f, (height - carH) / 2f), Size(carW, carH))
    return StageGeometry(compact, pressure, car, readingW = pressure * 4.6f, readingH = pressure * 2.3f)
}
