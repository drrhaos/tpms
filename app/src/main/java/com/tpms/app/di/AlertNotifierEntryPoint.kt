package com.tpms.app.di

import com.tpms.app.service.AlertNotifier
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AlertNotifierEntryPoint {
    fun alertNotifier(): AlertNotifier
}
