package com.tpms.app.service

import android.content.Context
import android.hardware.usb.UsbDevice
import com.tpms.app.data.persistence.ServiceHeartbeatStore
import com.tpms.app.data.repository.TpmsRepository
import com.tpms.app.data.usb.UsbDebugLog
import com.tpms.app.data.usb.UsbPermissionHelper
import com.tpms.app.domain.UsbPermissionDecision
import com.tpms.app.domain.UsbPermissionPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every USB permission request goes through here (wayfinder 14): the service's scan, the attach
 * broadcast / activity, the header tap and the notification action. Applies [UsbPermissionPolicy]
 * atomically on the state in [TpmsRepository] (which publishes `UsbFault.NO_PERMISSION`), calls
 * `requestPermission` when the policy says so, counts requests for diagnostics and keeps the quiet
 * "USB permission required" notification in line with the fault.
 */
@Singleton
class UsbPermissionRequester @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TpmsRepository,
    private val helper: UsbPermissionHelper,
    private val heartbeatStore: ServiceHeartbeatStore,
    private val debugLog: UsbDebugLog
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The notification state last applied; null = unknown (a notification may be left from an earlier process). */
    private var notified: Boolean? = null
    private val notifyLock = Any()

    /** The service's scan found [device] (null = no dongle); also used by the attach paths. */
    fun onDongleScanned(device: UsbDevice?) {
        val granted = device?.let(repository::hasUsbPermission) ?: false
        val decision = repository.decideUsbPermission(
            { UsbPermissionPolicy.onScan(it, device?.deviceName, granted) },
            UsbPermissionDecision::state
        )
        apply(decision, device, explicit = false)
    }

    /** Applies the policy to the dongle in the device list now (app launch). */
    fun scanNow() = onDongleScanned(repository.findDongle())

    /**
     * The user asked (header tap, notification action): request for the dongle in the device list
     * even after a denial. Without a dongle, or with permission already there, just wakes the search.
     */
    fun requestExplicitly() {
        val device = repository.findDongle()
        val granted = device?.let(repository::hasUsbPermission) ?: false
        val decision = repository.decideUsbPermission(
            { UsbPermissionPolicy.onExplicitRequest(it, device?.deviceName, granted) },
            UsbPermissionDecision::state
        )
        debugLog.usb(TAG, "Explicit permission request: ${device?.deviceName ?: "no dongle"} granted=$granted")
        apply(decision, device, explicit = true)
        if (!decision.request) TpmsMonitorService.wake(context)
    }

    /** Result of a `requestPermission` dialog (UsbPermissionReceiver). */
    fun onPermissionResult(granted: Boolean) {
        repository.decideUsbPermission(
            { if (granted) UsbPermissionPolicy.onGranted(it) else UsbPermissionPolicy.onDenied(it) },
            { it }
        )
        if (!granted) {
            debugLog.warn(TAG, "USB permission denied — not asking again until the dongle reappears or the user asks")
            scope.launch { runCatching { heartbeatStore.recordUsbPermissionDenied() } }
        }
        syncNotification()
    }

    /** A dongle was detached ([deviceName] null = unknown): its appearance is over. */
    fun onDongleGone(deviceName: String?) {
        repository.decideUsbPermission({ UsbPermissionPolicy.onGone(it, deviceName) }, { it })
        syncNotification()
    }

    private fun apply(decision: UsbPermissionDecision, device: UsbDevice?, explicit: Boolean) {
        syncNotification()
        if (!decision.request || device == null) return
        debugLog.usb(TAG, "Requesting USB permission for ${device.deviceName} (${if (explicit) "user" else "auto"})")
        runCatching { helper.requestPermission(context, device) }
            .onSuccess { scope.launch { runCatching { heartbeatStore.recordUsbPermissionRequest(explicit) } } }
            .onFailure { debugLog.exception(TAG, it, "requestPermission") }
    }

    /** Shows the quiet notification while the fault is on, dismisses it once off; posts only on change. */
    private fun syncNotification() {
        synchronized(notifyLock) {
            // Read under the lock, so concurrent callers end on the newest state.
            val fault = repository.usbPermission.fault
            if (notified == fault) return
            notified = fault
            runCatching {
                if (fault) UsbPermissionNotifier.show(context) else UsbPermissionNotifier.dismiss(context)
            }
        }
    }

    companion object {
        private const val TAG = "UsbPermission"
    }
}
