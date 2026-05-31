package com.tpms.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SensorDao {

    @Insert
    suspend fun insert(reading: SensorReading)

    @Query("SELECT * FROM sensor_readings WHERE sensorId = :sensorId ORDER BY timestamp DESC LIMIT 1")
    suspend fun lastReading(sensorId: String): SensorReading?

    @Query("SELECT * FROM sensor_readings WHERE sensorId = :sensorId ORDER BY timestamp DESC LIMIT 100")
    fun history(sensorId: String): Flow<List<SensorReading>>

    @Query("SELECT * FROM sensor_readings ORDER BY timestamp DESC LIMIT :limit")
    fun recentReadings(limit: Int = 20): Flow<List<SensorReading>>

    @Query("DELETE FROM sensor_readings WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}
