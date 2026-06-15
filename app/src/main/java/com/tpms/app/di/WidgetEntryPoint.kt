package com.tpms.app.di

import com.tpms.app.ui.widget.WidgetSnapshotBuilder
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetSnapshotBuilder(): WidgetSnapshotBuilder
}
