package com.tpms.app.ui.main

import androidx.annotation.DrawableRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.tpms.app.R
import kotlin.math.min

// Geometry of the x-ray car renders (tools/car_render), shared by the main screen and the
// home-screen widget so both show the same scene.

/**
 * Width / height of `car_3d_*` and `car_3d_wheel_*` (Blender x-ray render from behind-right: the car
 * drives away from the viewer, lower left to upper right, left wheels on the left; tools/car_render,
 * camera 200° / 38°).
 */
internal const val CAR_IMAGE_ASPECT = 1293f / 1058f

/** The car takes this share of the stage: room for the road around it and for large readings. */
internal const val CAR_SCALE = 0.8f

/**
 * `car_3d_env_*` (road, lane lines, floor grid, light pool; same camera, wider frame) in fractions
 * of the car image: it lines up with the car when scaled by the car's size.
 */
internal val ENV_RECT = Rect(Offset(-1.1655f, -0.7202f), Size(3.2483f, 2.1172f))

/**
 * Each wheel: its tint mask (tyre + rim, rendered with the same camera, shaded so the spokes stay
 * readable) and where the widget puts its reading, as a fraction of the car image: on its axle's
 * line extended past the wheel (hubs FL 0.417,0.169 / FR 0.702,0.242 / RL 0.197,0.578 / RR
 * 0.527,0.680), 0.27 of the image width beyond the hub, outside the body. The main screen puts the
 * readings in the stage corners instead ([mainStageLayout]).
 */
internal data class WheelSpot(val slot: String, @DrawableRes val mask: Int, val reading: Offset)

internal val WHEEL_SPOTS = listOf(
    WheelSpot("FL", R.drawable.car_3d_wheel_fl, Offset(0.153f, 0.101f)),
    WheelSpot("FR", R.drawable.car_3d_wheel_fr, Offset(0.966f, 0.310f)),
    WheelSpot("RL", R.drawable.car_3d_wheel_rl, Offset(-0.065f, 0.497f)),
    WheelSpot("RR", R.drawable.car_3d_wheel_rr, Offset(0.789f, 0.761f))
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
