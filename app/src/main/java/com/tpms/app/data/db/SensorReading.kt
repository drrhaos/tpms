package com.tpms.app.data.db

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "sensor_readings",
    indices = [Index(value = ["sensorId", "timestamp"], orders = [Index.Order.ASC, Index.Order.DESC])]
)
data class SensorReading(
    @androidx.room.PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sensorId: String,
    val pressureKpa: Float,
    val temperatureCelsius: Float,
    val batteryPercent: Int,
    val alertType: String?,
    val timestamp: Long
)
