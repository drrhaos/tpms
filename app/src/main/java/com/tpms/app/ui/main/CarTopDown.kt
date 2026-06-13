package com.tpms.app.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tpms.app.R
import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.PressureUnit
import com.tpms.app.domain.model.TireSensor
import com.tpms.app.ui.theme.StatusColors

private data class WheelPos(val xFrac: Float, val yFrac: Float, val label: String)

private val WHEELS = listOf(
    WheelPos(0.25f, 0.25f, "FL"),
    WheelPos(0.75f, 0.25f, "FR"),
    WheelPos(0.25f, 0.75f, "RL"),
    WheelPos(0.75f, 0.75f, "RR"),
)

@Composable
fun CarTopDown(
    sensors: List<TireSensor?>,
    pressureUnit: PressureUnit = PressureUnit.PSI,
    modifier: Modifier = Modifier
) {
    val cardW = 400.dp
    val cardH = 200.dp
    val margin = 8.dp

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(Color(0xFF121212), RoundedCornerShape(12.dp))
    ) {
        val boxW = maxWidth
        val boxH = maxHeight
        val imgSide = if (boxW < boxH) boxW else boxH
        val offsetX = (boxW - imgSide) / 2f
        val offsetY = (boxH - imgSide) / 2f

        Image(
            painter = painterResource(R.drawable.auto),
            contentDescription = "Car top-down view",
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A2E), RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit
        )

        WHEELS.forEachIndexed { index, pos ->
            val sensor = sensors.getOrNull(index)
            val dotColor = when {
                sensor == null -> StatusColors.disconnected
                sensor.alertType == AlertType.LOW_PRESSURE || sensor.alertType == AlertType.HIGH_PRESSURE ->
                    StatusColors.alert
                sensor.alertType == AlertType.HIGH_TEMP -> StatusColors.warning
                else -> StatusColors.ok
            }

            val isLeft = index % 2 == 0
            val wheelCenterX = offsetX + imgSide * pos.xFrac
            val wheelCenterY = offsetY + imgSide * pos.yFrac

            val cardX: Dp
            val cardAlign: Alignment.Horizontal
            if (isLeft) {
                cardX = margin
                cardAlign = Alignment.Start
            } else {
                cardX = boxW - cardW - margin
                cardAlign = Alignment.End
            }
            val cardY = (wheelCenterY - cardH / 2f).coerceIn(margin, boxH - cardH - margin)

            Box(
                modifier = Modifier
                    .offset(x = cardX, y = cardY)
                    .width(cardW)
                    .height(cardH)
                    .clip(RoundedCornerShape(8.dp))
                    .background(dotColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = pos.label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = dotColor.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = if (sensor != null)
                            "%.0f".format(pressureUnit.fromKpa(sensor.pressureKpa))
                        else "--",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = dotColor,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = if (sensor != null)
                            "%.0f°C".format(sensor.temperatureCelsius)
                        else "--°C",
                        fontSize = 14.sp,
                        color = dotColor.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Box(
                modifier = Modifier
                    .offset(x = wheelCenterX - 4.dp, y = wheelCenterY - 4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
    }
}
