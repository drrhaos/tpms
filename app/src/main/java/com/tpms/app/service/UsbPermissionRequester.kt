package com.tpms.app.service

import android.content.Context
import android.hardware.usb.UsbDevice
import android.os.SystemClock
import com.tpms.app.data.persistence.ServiceHeartbeatStore
import com.tpms.app.data.repository.TpmsRepository
import com.tpms.app.data.usb.UsbDebugLog
import com.tpms.app.data.usb.UsbPermissionHelper
import com.tpms.app.domain.UsbPermissionDecision
import com.tpms.app.domain.UsbPermissionPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every USB permission request goes through here (wayfinder 14): the service's scan, the attach
 * broadcast / activity, the header tap and the notification action. Applies [UsbPermissionPolicy]
 * atomically on the state in [TpmsRepository] (which publishes `UsbFault.NO_PERMISSION`), calls
 * `requestPermission` when the policy says so, counts requests for diagnostics and keeps the quiet
 * "USB permission required" notification in line with the fault.
 *
 * Grace period: the policy holds the automatic request until a dongle has been present without
 * permission for [UsbPermissionPolicy.AUTO_REQUEST_GRACE_MS] (the default-association grant may
 * land after the attach). The service's scan would come back within its search backoff, but the
 * attach paths may run without a running service (or one paused by recovery), so the requester
 * itself rescans once the grace period is over: an attach never loses its request.
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

    /**
     * The notification state last applied: true = posted, false = not shown (dismissed, swiped, or
     * the post was refused because notifications are not allowed), null = unknown (a notification
     * may be left from an earlier process). Only a real post sets true.
     */
    private var notified: Boolean? = null
    private val notifyLock = Any()

    /** The pending rescan after the grace period (at most one). */
    private var recheckJob: Job? = null
    private val recheckLock = Any()

    /** The service's scan found [device] (null = no dongle); also used by the attach paths. */
    fun onDongleScanned(device: UsbDevice?) {
        val granted = device?.let(repository::hasUsbPermission) ?: false
        val now = SystemClock.elapsedRealtime()
        val decision = repository.decideUsbPermission(
            { UsbPermissionPolicy.onScan(it, device?.deviceName, granted, now) },
            UsbPermissionDecision::state
        )
        apply(decision, device, explicit = false)
        decision.recheckAfterMs?.let(::scheduleRecheck)
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

    /**
     * Forgets what the notification shows and re-applies the fault: posts it if still needed
     * (e.g. notifications were allowed since), dismisses it otherwise. Service start / app resume.
     */
    fun resyncNotification() {
        synchronized(notifyLock) { notified = null }
        syncNotification()
    }

    /**
     * Monitoring stopped (stop action / service destroyed): the ongoing notification must not
     * outlive it. The fault state is kept; the next service start re-syncs and posts it again.
     */
    fun onMonitoringStopped() {
        synchronized(recheckLock) {
            recheckJob?.cancel()
            recheckJob = null
        }
        synchronized(notifyLock) {
            notified = false
            runCatching { UsbPermissionNotifier.dismiss(context) }
        }
    }

    /**
     * The user swiped the notification away (Android 14+ allows it for ongoing non-FGS ones).
     * Not re-posted here (that would fight the swipe): the next scan / sync posts it again while
     * the fault persists.
     */
    fun onNotificationSwiped() {
        synchronized(notifyLock) { notified = false }
    }

    private fun scheduleRecheck(afterMs: Long) {
        synchronized(recheckLock) {
            recheckJob?.cancel()
            recheckJob = scope.launch {
                delay(afterMs + RECHECK_SLACK_MS)
                runCatching { recheck() }.onFailure { debugLog.exception(TAG, it, "grace recheck") }
            }
        }
    }

    /** The grace period is over: ask now if still without permission; wake the service if the default grant landed. */
    private fun recheck() {
        val device = repository.findDongle()
        onDongleScanned(device)
        if (device != null && repository.hasUsbPermission(device)) {
            TpmsMonitorService.wake(context, deviceAttached = true)
        }
    }

    private fun apply(decision: UsbPermissionDecision, device: UsbDevice?, explicit: Boolean) {
        syncNotification()
        if (!decision.request || device == null) return
        debugLog.usb(TAG, "Requesting USB permission for ${device.deviceName} (${if (explicit) "user" else "auto"})")
        runCatching { helper.requestPermission(context, device) }
            .onSuccess { scope.launch { runCatching { heartbeatStore.recordUsbPermissionRequest(explicit) } } }
            .onFailure { debugLog.exception(TAG, it, "requestPermission") }
    }

    /**
     * Shows the quiet notification while the fault is on, dismisses it once off; acts only on
     * change. A refused post leaves [notified] false, so the next sync tries again.
     */
    private fun syncNotification() {
        synchronized(notifyLock) {
            // Read under the lock, so concurrent callers end on the newest state.
            val fault = repository.usbPermission.fault
            if (notified == fault) return
            notified = if (fault) {
                runCatching { UsbPermissionNotifier.show(context) }.getOrDefault(false)
            } else {
                runCatching { UsbPermissionNotifier.dismiss(context) }
                false
            }
        }
    }

    companion object {
        private const val TAG = "UsbPermission"
        /** Past the grace period, so the rescan is not a hair early. */
        private const val RECHECK_SLACK_MS = 100L
    }
}
