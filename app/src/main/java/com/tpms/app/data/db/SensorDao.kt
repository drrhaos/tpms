package com.tpms.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SensorDao {

    @Insert
    suspend fun insert(reading: SensorReading)

    @Query("DELETE FROM sensor_readings WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}
