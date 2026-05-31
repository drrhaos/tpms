package com.tpms.app.di

import android.content.Context
import android.hardware.usb.UsbManager
import androidx.room.Room
import com.tpms.app.data.db.AppDatabase
import com.tpms.app.data.db.SensorDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideUsbManager(@ApplicationContext context: Context): UsbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "tpms.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideSensorDao(db: AppDatabase): SensorDao = db.sensorDao()
}
