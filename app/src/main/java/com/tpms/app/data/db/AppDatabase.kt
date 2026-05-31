package com.tpms.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import javax.inject.Inject
import javax.inject.Singleton

@Database(entities = [SensorReading::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sensorDao(): SensorDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "tpms.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
