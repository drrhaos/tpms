package com.tpms.app.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tpms.app.R
import com.tpms.app.domain.model.PressureUnit
import com.tpms.app.domain.model.TireSensor
import com.tpms.app.ui.localizedLabel
import com.tpms.app.ui.main.WHEEL_HUBS
import com.tpms.app.ui.main.WHEEL_SPOTS
import com.tpms.app.ui.main.clampOffset
import com.tpms.app.ui.main.stageGeometry
import com.tpms.app.ui.theme.StatusColors
import com.tpms.app.ui.theme.TpmsColors
import com.tpms.app.ui.wheelPositionLabel
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** The swaps of a tyre rotation: along each side and across each axle. */
private val SWAPS = listOf("FL" to "RL", "FR" to "RR", "FL" to "FR", "RL" to "RR")

/**
 * Tyre rotation on the main screen's car: each wheel shows its sensor and pressure, and a double
 * arrow between two wheels swaps their sensors ([onSwap]); tapping it again swaps them back.
 */
@Composable
fun WheelSwapDiagram(
    wheels: Map<String, TireSensor>,
    unit: PressureUnit,
    onSwap: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.fillMaxWidth().height(320.dp)) {
        val geo = stageGeometry(maxWidth.value, maxHeight.value, compact = true, carScale = 0.9f)
        val car = geo.car
        fun hub(slot: String): Offset {
            val h = WHEEL_HUBS.getValue(slot)
            return Offset(car.left + h.x * car.width, car.top + h.y * car.height)
        }
        val carModifier = Modifier
            .offset(car.left.dp, car.top.dp)
            .size(car.width.dp, car.height.dp)

        Image(
            painter = painterResource(if (TpmsColors.isDark) R.drawable.car_3d_dark else R.drawable.car_3d_light),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = carModifier
        )
        WHEEL_SPOTS.forEach { spot ->
            Image(
                painter = painterResource(spot.mask),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(
                    if (wheels[spot.slot] != null) StatusColors.ok else TpmsColors.onSurfaceMuted,
                    BlendMode.Modulate
                ),
                alpha = 0.92f,
                modifier = carModifier
            )
        }

        // Double arrows from tyre to tyre, stopping short of the tyres themselves.
        val arrowColor = MaterialTheme.colorScheme.primary
        val tyreRadius = car.width * 0.075f
        Canvas(Modifier.fillMaxSize()) {
            SWAPS.forEach { (a, b) ->
                val pa = hub(a); val pb = hub(b)
                val len = hypot(pb.x - pa.x, pb.y - pa.y)
                if (len <= 2 * tyreRadius) return@forEach
                val dir = Offset((pb.x - pa.x) / len, (pb.y - pa.y) / len)
                val start = (pa + dir * tyreRadius) * density
                val end = (pb - dir * tyreRadius) * density
                val stroke = 2.5.dp.toPx()
                drawLine(arrowColor, start, end, stroke, StrokeCap.Round)
                val head = 10.dp.toPx()
                listOf(start to dir, end to -dir).forEach { (tip, d) ->
                    listOf(0.5f, -0.5f).forEach { turn ->
                        val c = cos(turn); val s = sin(turn)
                        val back = Offset(d.x * c - d.y * s, d.x * s + d.y * c) * head
                        drawLine(arrowColor, tip, tip + back, stroke, StrokeCap.Round)
                    }
                }
            }
        }

        SWAPS.forEach { (a, b) ->
            val pa = hub(a); val pb = hub(b)
            val mid = (pa + pb) / 2f
            val angle = Math.toDegrees(atan2((pb.y - pa.y).toDouble(), (pb.x - pa.x).toDouble())).toFloat()
            val action = stringResource(R.string.settings_wheels_swap_action, wheelPositionLabel(a), wheelPositionLabel(b))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset((mid.x - SWAP_BUTTON / 2).dp, (mid.y - SWAP_BUTTON / 2).dp)
                    .size(SWAP_BUTTON.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(2.dp, arrowColor, CircleShape)
                    .clickable { onSwap(a, b) }
                    .semantics { contentDescription = action }
            ) {
                Icon(
                    imageVector = Icons.Filled.SwapHoriz,
                    contentDescription = null,
                    tint = arrowColor,
                    modifier = Modifier.size(20.dp).rotate(angle)
                )
            }
        }

        WHEEL_SPOTS.forEach { spot ->
            val w = geo.readingW
            val cx = car.left + spot.reading.x * car.width
            val cy = car.top + spot.reading.y * car.height
            WheelSensorLabel(
                slot = spot.slot,
                sensor = wheels[spot.slot],
                unit = unit,
                modifier = Modifier
                    .offset(
                        clampOffset(cx - w / 2f, 0f, maxWidth.value - w).dp,
                        clampOffset(cy - LABEL_HEIGHT / 2f, 0f, maxHeight.value - LABEL_HEIGHT).dp
                    )
                    .width(w.dp)
            )
        }
    }
}

private const val SWAP_BUTTON = 34f
private const val LABEL_HEIGHT = 54f

/** Wheel name, the sensor's pressure and its id (or "—" without a sensor). */
@Composable
private fun WheelSensorLabel(slot: String, sensor: TireSensor?, unit: PressureUnit, modifier: Modifier) {
    val muted = TpmsColors.onSurfaceMuted
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(wheelPositionLabel(slot), fontSize = 12.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            text = sensor?.pressureKpa?.takeIf { it.isFinite() }
                ?.let { "${unit.formatFromKpa(it)} ${unit.localizedLabel()}" }
                ?: stringResource(R.string.value_em_dash),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (sensor != null) TpmsColors.onBackground else muted,
            maxLines = 1
        )
        Text(
            text = sensor?.id ?: stringResource(R.string.value_em_dash),
            fontSize = 11.sp,
            color = muted,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
