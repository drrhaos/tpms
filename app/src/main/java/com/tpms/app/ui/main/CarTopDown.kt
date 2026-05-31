package com.tpms.app.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tpms.app.R
import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.PressureUnit
import com.tpms.app.domain.model.TireSensor
import com.tpms.app.ui.theme.StatusColors

private data class WheelPos(val xFrac: Float, val yFrac: Float)

private val WHEELS = listOf(
    WheelPos(0.25f, 0.25f),  // FL (top-left)
    WheelPos(0.75f, 0.25f),  // RL (top-right)
    WheelPos(0.25f, 0.75f),  // FR (bottom-left)
    WheelPos(0.75f, 0.75f),  // RR (bottom-right)
)

@Composable
fun CarTopDown(
    sensors: List<TireSensor?>,
    pressureUnit: PressureUnit = PressureUnit.PSI,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                WheelLabel("FL", sensors.getOrNull(0), pressureUnit)
                WheelLabel("FR", sensors.getOrNull(1), pressureUnit)
            }

            Spacer(modifier = Modifier.height(4.dp))

            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                val boxW = maxWidth
                val boxH = maxHeight
                val imgSide = if (boxW < boxH) boxW else boxH
                val offsetX = (boxW - imgSide) / 2f
                val offsetY = (boxH - imgSide) / 2f
                val wheelSize = 32.dp
                val halfWheel = 16.dp

                Image(
                    painter = painterResource(R.drawable.auto),
                    contentDescription = "Car top-down view",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                WHEELS.forEachIndexed { index, pos ->
                    val sensor = sensors.getOrNull(index)
                    val (bgColor, borderColor) = when {
                        sensor == null -> StatusColors.disconnected.copy(alpha = 0.5f) to StatusColors.disconnected
                        sensor.alertType == AlertType.LOW_PRESSURE || sensor.alertType == AlertType.HIGH_PRESSURE ->
                            StatusColors.alert.copy(alpha = 0.6f) to StatusColors.alert
                        sensor.alertType == AlertType.HIGH_TEMP ->
                            StatusColors.warning.copy(alpha = 0.6f) to StatusColors.warning
                        else -> StatusColors.ok.copy(alpha = 0.5f) to StatusColors.ok
                    }

                    Box(
                        modifier = Modifier
                            .offset(
                                x = offsetX + imgSide * pos.xFrac - halfWheel,
                                y = offsetY + imgSide * pos.yFrac - halfWheel
                            )
                            .size(wheelSize),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.fillMaxSize().clip(CircleShape).background(bgColor))
                        Text(
                            text = if (sensor != null) "%.0f".format(pressureUnit.fromKpa(sensor.pressureKpa)) else "--",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = borderColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                WheelLabel("RL", sensors.getOrNull(2), pressureUnit)
                WheelLabel("RR", sensors.getOrNull(3), pressureUnit)
            }
        }
    }
}

@Composable
private fun WheelLabel(label: String, sensor: TireSensor?, unit: PressureUnit = PressureUnit.PSI) {
    val color = when {
        sensor == null -> StatusColors.disconnected
        sensor.isAlert -> StatusColors.alert
        else -> StatusColors.ok
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        if (sensor != null) {
            Text(
                text = "%.1f %s".format(unit.fromKpa(sensor.pressureKpa), unit.label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}


