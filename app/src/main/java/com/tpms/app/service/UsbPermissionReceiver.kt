package com.tpms.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.util.Log
import com.tpms.app.di.UsbReceiverEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * - [ACTION_USB_PERMISSION]: result of a `requestPermission` dialog. Granted wakes the service like
 *   an attach; denied is remembered so the dongle is not asked for again until it reappears.
 * - [ACTION_REQUEST_USB_PERMISSION]: the "Request permission" notification action (explicit request).
 * - [ACTION_PERMISSION_NOTIFICATION_SWIPED]: the notification was swiped away; re-posted on the next scan.
 */
class UsbPermissionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ACTION_USB_PERMISSION &&
            action != ACTION_REQUEST_USB_PERMISSION &&
            action != ACTION_PERMISSION_NOTIFICATION_SWIPED
        ) {
            return
        }
        val requester = EntryPointAccessors.fromApplication(
            context.applicationContext,
            UsbReceiverEntryPoint::class.java
        ).usbPermissionRequester()

        if (action == ACTION_PERMISSION_NOTIFICATION_SWIPED) {
            runCatching { requester.onNotificationSwiped() }
                .onFailure { Log.w(TAG, "Swipe not applied", it) }
            return
        }

        if (action == ACTION_REQUEST_USB_PERMISSION) {
            Log.d(TAG, "Permission requested from the notification")
            runCatching { requester.requestExplicitly() }
                .onFailure { Log.w(TAG, "Explicit permission request failed", it) }
            return
        }

        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
        runCatching { requester.onPermissionResult(granted) }
            .onFailure { Log.w(TAG, "Permission result not applied", it) }
        if (granted) {
            Log.d(TAG, "USB permission granted — waking service")
            // Granted right after an attach of a dongle without permission: treat it as that attach.
            TpmsMonitorService.wake(context, deviceAttached = true)
        } else {
            Log.w(TAG, "USB permission denied")
        }
    }

    companion object {
        private const val TAG = "UsbPermissionReceiver"
        const val ACTION_USB_PERMISSION = "com.tpms.app.action.USB_PERMISSION"
        const val ACTION_REQUEST_USB_PERMISSION = "com.tpms.app.action.REQUEST_USB_PERMISSION"
        const val ACTION_PERMISSION_NOTIFICATION_SWIPED = "com.tpms.app.action.USB_PERMISSION_NOTIFICATION_SWIPED"
    }
}
