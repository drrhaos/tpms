package com.tpms.app.ui.settings

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.tpms.app.ui.theme.StatusColors
import com.tpms.app.ui.theme.TpmsColors
import com.tpms.app.ui.wheelPositionLabel
import kotlin.math.min

/** Top view, nose up, in dp from the diagram's centre. */
private const val BODY_W = 110f
private const val BODY_H = 240f
private const val TYRE_W = 24f
private const val TYRE_H = 50f
private const val TRACK = 62f     // hub x from the centre line
private const val AXLE = 75f      // hub y from the centre
private const val LABEL_GAP = 84f // label edge from the centre line
private const val SWAP_BUTTON = 36f

/** The swaps of a tyre rotation: along each side and across each axle. */
private val SWAPS = listOf("FL" to "RL", "FR" to "RR", "FL" to "FR", "RL" to "RR")

/** The cross rotation's two diagonals, swapped together by the button in the middle. */
private val CROSS = listOf("FL" to "RR", "FR" to "RL")

private fun hubOf(slot: String) = when (slot) {
    "FL" -> Offset(-TRACK, -AXLE)
    "FR" -> Offset(TRACK, -AXLE)
    "RL" -> Offset(-TRACK, AXLE)
    else -> Offset(TRACK, AXLE)
}

/**
 * Tyre rotation on a plain top view of the car: each wheel shows its sensor and pressure, and a
 * double arrow between two wheels swaps their sensors ([onSwap]); the dashed diagonals' button in
 * the middle swaps both diagonals at once ([onSwapCross]). Tapping again swaps back.
 */
@Composable
fun WheelSwapDiagram(
    wheels: Map<String, TireSensor>,
    unit: PressureUnit,
    onSwap: (String, String) -> Unit,
    onSwapCross: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.fillMaxWidth().height(300.dp)) {
        val centre = Offset(maxWidth.value / 2f, maxHeight.value / 2f)
        val arrowColor = MaterialTheme.colorScheme.primary
        val bodyColor = MaterialTheme.colorScheme.surfaceVariant
        val outline = MaterialTheme.colorScheme.outline
        val glass = TpmsColors.onSurfaceMuted.copy(alpha = 0.35f)
        val noData = TpmsColors.onSurfaceMuted

        Canvas(Modifier.fillMaxSize()) {
            val c = centre * density
            drawCarBody(c, bodyColor, outline, glass)
            listOf("FL", "FR", "RL", "RR").forEach { slot ->
                val hub = c + hubOf(slot) * density
                val size = Size(TYRE_W.dp.toPx(), TYRE_H.dp.toPx())
                drawRoundRect(
                    color = if (wheels[slot] != null) StatusColors.ok else noData,
                    topLeft = hub - Offset(size.width / 2f, size.height / 2f),
                    size = size,
                    cornerRadius = CornerRadius(6.dp.toPx())
                )
            }
            SWAPS.forEach { (a, b) ->
                val pa = c + hubOf(a) * density
                val pb = c + hubOf(b) * density
                // From tyre edge to tyre edge: past the tyre's half length (side) or half width (axle).
                val inset = if (pa.x == pb.x) (TYRE_H / 2f + 6f).dp.toPx() else (TYRE_W / 2f + 6f).dp.toPx()
                drawDoubleArrow(pa, pb, inset, arrowColor)
            }
            // Diagonals: dashed and fainter, so the four plain swaps still read first.
            CROSS.forEach { (a, b) ->
                drawDoubleArrow(
                    c + hubOf(a) * density, c + hubOf(b) * density, (TYRE_H / 2f + 4f).dp.toPx(),
                    arrowColor.copy(alpha = 0.6f), dashed = true
                )
            }
        }

        SwapButton(
            centre = centre,
            icon = Icons.Filled.Shuffle,
            description = stringResource(R.string.settings_wheels_swap_cross_action),
            color = arrowColor,
            onClick = onSwapCross
        )

        SWAPS.forEach { (a, b) ->
            val mid = centre + (hubOf(a) + hubOf(b)) / 2f
            val vertical = hubOf(a).x == hubOf(b).x
            SwapButton(
                centre = mid,
                icon = if (vertical) Icons.Filled.SwapVert else Icons.Filled.SwapHoriz,
                description = stringResource(R.string.settings_wheels_swap_action, wheelPositionLabel(a), wheelPositionLabel(b)),
                color = arrowColor,
                onClick = { onSwap(a, b) }
            )
        }

        val labelW = min(160f, centre.x - LABEL_GAP).coerceAtLeast(60f)
        listOf("FL", "FR", "RL", "RR").forEach { slot ->
            val left = slot.endsWith("L")
            val hub = centre + hubOf(slot)
            WheelSensorLabel(
                slot = slot,
                sensor = wheels[slot],
                unit = unit,
                alignEnd = left,
                modifier = Modifier
                    .offset(
                        (if (left) centre.x - LABEL_GAP - labelW else centre.x + LABEL_GAP).dp,
                        (hub.y - LABEL_HEIGHT / 2f).dp
                    )
                    .width(labelW.dp)
            )
        }
    }
}

private const val LABEL_HEIGHT = 54f

/** Round button centred on [centre] (dp from the diagram's top-left). */
@Composable
private fun SwapButton(centre: Offset, icon: ImageVector, description: String, color: Color, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .offset((centre.x - SWAP_BUTTON / 2).dp, (centre.y - SWAP_BUTTON / 2).dp)
            .size(SWAP_BUTTON.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(2.dp, color, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
    }
}

/** Body with rounded nose and tail, windscreen at the top, rear window at the bottom. */
private fun DrawScope.drawCarBody(
    c: Offset,
    body: Color,
    outline: Color,
    glass: Color
) {
    val w = BODY_W.dp.toPx(); val h = BODY_H.dp.toPx()
    val topLeft = Offset(c.x - w / 2f, c.y - h / 2f)
    drawRoundRect(body, topLeft, Size(w, h), CornerRadius(w * 0.38f))
    drawRoundRect(outline, topLeft, Size(w, h), CornerRadius(w * 0.38f), style = Stroke(1.5.dp.toPx()))
    val glassW = w * 0.74f
    drawRoundRect(glass, Offset(c.x - glassW / 2f, c.y - h * 0.22f), Size(glassW, h * 0.13f), CornerRadius(8.dp.toPx()))
    drawRoundRect(glass, Offset(c.x - glassW / 2f, c.y + h * 0.17f), Size(glassW, h * 0.09f), CornerRadius(8.dp.toPx()))
}

private fun DrawScope.drawDoubleArrow(
    from: Offset,
    to: Offset,
    inset: Float,
    color: Color,
    dashed: Boolean = false
) {
    val d = to - from
    val dir = d / d.getDistance()
    val start = from + dir * inset
    val end = to - dir * inset
    val stroke = 2.5.dp.toPx()
    val dash = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())) else null
    drawLine(color, start, end, stroke, StrokeCap.Round, pathEffect = dash)
    val head = 9.dp.toPx()
    val side = Offset(-dir.y, dir.x) * head * 0.6f
    listOf(start to dir, end to -dir).forEach { (tip, forward) ->
        drawLine(color, tip, tip + forward * head + side, stroke, StrokeCap.Round)
        drawLine(color, tip, tip + forward * head - side, stroke, StrokeCap.Round)
    }
}

/** Wheel name, the sensor's pressure and its id (or "—" without a sensor), aligned to its wheel. */
@Composable
private fun WheelSensorLabel(
    slot: String,
    sensor: TireSensor?,
    unit: PressureUnit,
    alignEnd: Boolean,
    modifier: Modifier
) {
    val muted = TpmsColors.onSurfaceMuted
    val align = if (alignEnd) TextAlign.End else TextAlign.Start
    Column(
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        modifier = modifier
    ) {
        Text(wheelPositionLabel(slot), fontSize = 12.sp, color = muted, textAlign = align, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            text = sensor?.pressureKpa?.takeIf { it.isFinite() }
                ?.let { "${unit.formatFromKpa(it)} ${unit.localizedLabel()}" }
                ?: stringResource(R.string.value_em_dash),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (sensor != null) TpmsColors.onBackground else muted,
            textAlign = align,
            maxLines = 1
        )
        Text(
            text = sensor?.id ?: stringResource(R.string.value_em_dash),
            fontSize = 11.sp,
            color = muted,
            textAlign = align,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
