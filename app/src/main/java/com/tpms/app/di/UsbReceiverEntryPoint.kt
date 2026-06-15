package com.tpms.app.di

import com.tpms.app.data.persistence.ServiceHeartbeatStore
import com.tpms.app.data.usb.DongleDetector
import com.tpms.app.data.usb.UsbDebugLog
import com.tpms.app.data.usb.UsbPermissionHelper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UsbReceiverEntryPoint {
    fun debugLog(): UsbDebugLog
    fun dongleDetector(): DongleDetector
    fun usbPermissionHelper(): UsbPermissionHelper
    fun serviceHeartbeatStore(): ServiceHeartbeatStore
}
