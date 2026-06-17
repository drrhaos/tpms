package com.tpms.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tpms.app.R
import com.tpms.app.domain.AlertSeverity
import com.tpms.app.domain.model.TireSensor
import com.tpms.app.domain.toSeverity
import com.tpms.app.ui.theme.StatusColors = Color(0xFF1A1F26)
private val TireInner = Color(0xFF0D1117)
private val RimLight = Color(0xFF8B95A8)
private val RimMid = Color(0xFF4A5568)
private val RimDark = Color(0xFF252D3A)
private val HubLight = Color(0xFFB8C2D0)
private val HubDark = Color(0xFF3D4654)
private val SpokeColor = Color(0xFF5C6778)

@Composable
fun WheelStatusIcon(
    sensor: TireSensor?,
    modifier: Modifier = Modifier,
    iconSize: Dp = 36.dp
) {
    val isOk = sensor?.toSeverity() == AlertSeverity.OK
    val statusColor = if (isOk) StatusColors.ok else StatusColors.alert
    val description = if (isOk) {
        stringResource(R.string.wheel_status_ok)
    } else {
        stringResource(R.string.wheel_status_problem)
    }

    Canvas(
        modifier = modifier
            .size(iconSize)
            .semantics { contentDescription = description }
    ) {
        val side = size.minDimension
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerR = side * 0.48f
        val tireOuterR = outerR * 0.96f
        val tireInnerR = outerR * 0.78f
        val rimOuterR = tireInnerR * 0.98f
        val rimInnerR = outerR * 0.42f
        val hubR = outerR * 0.16f

        drawCircle(
            color = statusColor.copy(alpha = 0.22f),
            radius = outerR * 1.06f,
            center = center
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(statusColor.copy(alpha = 0.55f), statusColor.copy(alpha = 0.08f)),
                center = center,
                radius = outerR
            ),
            radius = outerR,
            center = center
        )

        drawCircle(color = TireOuter, radius = tireOuterR, center = center)
        drawCircle(
            color = TireInner,
            radius = (tireOuterR + tireInnerR) / 2f,
            center = center,
            style = Stroke(width = tireOuterR - tireInnerR)
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(RimLight, RimMid, RimDark),
                center = center + Offset(-rimOuterR * 0.25f, -rimOuterR * 0.3f),
                radius = rimOuterR
            ),
            radius = rimOuterR,
            center = center
        )
        drawCircle(color = RimDark, radius = rimInnerR, center = center)

        drawSpokes(center = center, innerR = hubR * 1.15f, outerR = rimInnerR * 0.92f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(HubLight, HubDark),
                center = center + Offset(-hubR * 0.35f, -hubR * 0.35f),
                radius = hubR
            ),
            radius = hubR,
            center = center
        )

        drawCircle(
            color = statusColor,
            radius = outerR * 1.02f,
            center = center,
            style = Stroke(width = side * 0.05f)
        )
        drawCircle(
            color = Color.White.copy(alpha = if (isOk) 0.18f else 0.1f),
            radius = rimOuterR * 0.55f,
            center = center + Offset(-rimOuterR * 0.2f, -rimOuterR * 0.25f),
            style = Stroke(width = side * 0.02f)
        )
    }
}

private fun DrawScope.drawSpokes(
    center: Offset,
    innerR: Float,
    outerR: Float,
    count: Int = 5
) {
    val step = 360f / count
    repeat(count) { index ->
        rotate(degrees = step * index, pivot = center) {
            drawLine(
                color = SpokeColor,
                start = center + Offset(0f, -innerR),
                end = center + Offset(0f, -outerR),
                strokeWidth = (outerR - innerR) * 0.12f,
                cap = StrokeCap.Round
            )
        }
    }
}

fun TireSensor?.wheelStatusColor(): Color = when (this?.toSeverity()) {
    AlertSeverity.OK -> StatusColors.ok
    AlertSeverity.WARNING -> StatusColors.warning
    AlertSeverity.ALERT,
    AlertSeverity.DISCONNECTED -> StatusColors.alert
    null -> StatusColors.alert
}
