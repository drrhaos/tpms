package com.tpms.app.domain

/** What the app knows about the USB permission of the dongle in the device list. */
data class UsbPermissionState(
    /** deviceName of the current dongle appearance already asked for (automatically or by the user); null = none yet. */
    val requestedFor: String? = null,
    /** The user denied the last request (informational: the once-per-appearance rule already stops re-asking). */
    val denied: Boolean = false,
    /** A dongle is present without permission: [UsbFault.NO_PERMISSION]. */
    val fault: Boolean = false,
    /** deviceName seen without permission and not asked for yet (grace period running); null = none. */
    val firstSeenFor: String? = null,
    /** When [firstSeenFor] was first seen (monotonic ms, the caller's clock). */
    val firstSeenMs: Long = 0L
)

/**
 * [request]: call `UsbManager.requestPermission` for the dongle now.
 * [recheckAfterMs]: the automatic request is waiting for the grace period; scan again after this
 * many ms (null = nothing pending).
 */
data class UsbPermissionDecision(
    val request: Boolean,
    val state: UsbPermissionState,
    val recheckAfterMs: Long? = null
)

/**
 * When the app asks for USB permission itself (wayfinder 14).
 * - A dongle without permission is asked for automatically at most once per appearance: a new
 *   deviceName (re-enumeration gives a new one), or the dongle disappeared and came back. The
 *   service's scan and the attach paths (broadcast + activity) share this, so one attach shows one dialog.
 * - The automatic request waits until the dongle has been present without permission for
 *   [AUTO_REQUEST_GRACE_MS]: the device shows up in the device list (and ATTACHED fires) before
 *   the system's "use by default" grant lands, and asking in that window shows a needless dialog.
 *   No fault is raised during the grace period. Explicit requests do not wait.
 * - After a denial nothing is asked automatically until the next appearance; an explicit request
 *   (header tap, notification action) always asks.
 * - [UsbFault.NO_PERMISSION] is on while a dongle is present without permission, off once it is
 *   granted or the dongle is gone.
 */
object UsbPermissionPolicy {

    /** How long a dongle must be present without permission before it is asked for automatically. */
    const val AUTO_REQUEST_GRACE_MS = 1_500L

    /**
     * The service's scan or an attach saw [deviceName] (null = no dongle) with/without permission
     * at [nowMs] (monotonic clock).
     */
    fun onScan(state: UsbPermissionState, deviceName: String?, granted: Boolean, nowMs: Long): UsbPermissionDecision {
        if (deviceName == null) return UsbPermissionDecision(request = false, state = UsbPermissionState())
        if (granted) return UsbPermissionDecision(request = false, state = state.copy(denied = false, fault = false))
        if (deviceName == state.requestedFor) {
            return UsbPermissionDecision(request = false, state = state.copy(fault = true))
        }
        if (deviceName != state.firstSeenFor) {
            return UsbPermissionDecision(
                request = false,
                state = UsbPermissionState(firstSeenFor = deviceName, firstSeenMs = nowMs),
                recheckAfterMs = AUTO_REQUEST_GRACE_MS
            )
        }
        val elapsedMs = nowMs - state.firstSeenMs
        if (elapsedMs < AUTO_REQUEST_GRACE_MS) {
            return UsbPermissionDecision(
                request = false,
                state = state,
                recheckAfterMs = (AUTO_REQUEST_GRACE_MS - elapsedMs).coerceAtMost(AUTO_REQUEST_GRACE_MS)
            )
        }
        return UsbPermissionDecision(
            request = true,
            state = UsbPermissionState(requestedFor = deviceName, denied = false, fault = true)
        )
    }

    /** The user asked for permission (header tap, notification action): ask whatever happened before. */
    fun onExplicitRequest(state: UsbPermissionState, deviceName: String?, granted: Boolean): UsbPermissionDecision {
        if (deviceName == null) return UsbPermissionDecision(request = false, state = UsbPermissionState())
        if (granted) return UsbPermissionDecision(request = false, state = state.copy(denied = false, fault = false))
        return UsbPermissionDecision(
            request = true,
            state = UsbPermissionState(requestedFor = deviceName, denied = false, fault = true)
        )
    }

    fun onDenied(state: UsbPermissionState): UsbPermissionState = state.copy(denied = true)

    fun onGranted(state: UsbPermissionState): UsbPermissionState = state.copy(denied = false, fault = false)

    /** [deviceName] was detached (null = unknown): the appearance it belonged to is over. */
    fun onGone(state: UsbPermissionState, deviceName: String?): UsbPermissionState =
        if (deviceName == null || deviceName == state.requestedFor || deviceName == state.firstSeenFor) {
            UsbPermissionState()
        } else {
            state
        }

    /**
     * The one fault the UI shows. A stuck reader needs a reboot whatever else is wrong, so it wins;
     * a missing permission otherwise beats a silent-dongle streak (which may be stale, and "replug"
     * would not help while the permission is missing).
     */
    fun publishedFault(recoveryFault: UsbFault?, permissionFault: Boolean): UsbFault? = when {
        recoveryFault == UsbFault.USB_STUCK -> UsbFault.USB_STUCK
        permissionFault -> UsbFault.NO_PERMISSION
        else -> recoveryFault
    }
}
