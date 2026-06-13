package com.tpms.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tpms.app.domain.model.PressureUnit
import com.tpms.app.domain.model.TireSensor
import com.tpms.app.ui.components.TpmsCard
import com.tpms.app.ui.theme.StatusColors
import com.tpms.app.ui.theme.TpmsColors

@Composable
fun MiniDashboard(
    sensors: Map<String, TireSensor>,
    pressureUnit: PressureUnit = PressureUnit.PSI
) {
    TpmsCard(title = "Quick Status") {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val ordered = listOf("FL", "FR", "RL", "RR").mapNotNull { label ->
                sensors[label] ?: sensors.values.firstOrNull { it.label == label }
            }.ifEmpty { sensors.values.sortedBy { it.id }.take(4) }

            ordered.forEach { sensor ->
                TireIndicator(sensor = sensor, pressureUnit = pressureUnit)
            }
        }
    }
}

@Composable
private fun TireIndicator(sensor: TireSensor, pressureUnit: PressureUnit) {
    val color = when {
        sensor.isAlert -> StatusColors.alert
        sensor.alertType != null -> StatusColors.warning
        else -> StatusColors.ok
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f))
                .border(1.5.dp, color.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (sensor.pressureKpa.isFinite())
                    "%.0f".format(pressureUnit.fromKpa(sensor.pressureKpa))
                else "--",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = sensor.label.ifEmpty { sensor.id.take(4) },
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = TpmsColors.onSurfaceMuted,
            maxLines = 1
        )
    }
}
