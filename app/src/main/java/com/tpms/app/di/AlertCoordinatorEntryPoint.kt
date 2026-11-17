package com.tpms.app.di

import com.tpms.app.service.AlertCoordinator
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AlertCoordinatorEntryPoint {
    fun alertCoordinator(): AlertCoordinator
}
