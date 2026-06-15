package com.tpms.app.di

import com.tpms.app.data.diagnostics.UiBreadcrumbs
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UiBreadcrumbEntryPoint {
    fun uiBreadcrumbs(): UiBreadcrumbs
}
