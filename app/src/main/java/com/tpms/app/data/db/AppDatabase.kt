package com.tpms.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SensorReading::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sensorDao(): SensorDao
}
