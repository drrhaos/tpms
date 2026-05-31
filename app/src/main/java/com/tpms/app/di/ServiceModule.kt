package com.tpms.app.di

import com.tpms.app.domain.model.AlertThresholds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Provides
    @Singleton
    fun provideAlertThresholds(): AlertThresholds = AlertThresholds()
}
