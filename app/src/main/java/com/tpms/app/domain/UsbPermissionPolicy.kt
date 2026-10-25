package com.tpms.app.domain

/** What the app knows about the USB permission of the dongle in the device list. */
data class UsbPermissionState(
    /** deviceName of the current dongle appearance already asked for (automatically or by the user); null = none yet. */
    val requestedFor: String? = null,
    /** The user denied the last request (informational: the once-per-appearance rule already stops re-asking). */
    val denied: Boolean = false,
    /** A dongle is present without permission: [UsbFault.NO_PERMISSION]. */
    val fault: Boolean = false
)

/** [request]: call `UsbManager.requestPermission` for the dongle now. */
data class UsbPermissionDecision(val request: Boolean, val state: UsbPermissionState)

/**
 * When the app asks for USB permission itself (wayfinder 14).
 * - A dongle without permission is asked for automatically at most once per appearance: a new
 *   deviceName (re-enumeration gives a new one), or the dongle disappeared and came back. The
 *   service's scan and the attach paths (broadcast + activity) share this, so one attach shows one dialog.
 * - After a denial nothing is asked automatically until the next appearance; an explicit request
 *   (header tap, notification action) always asks.
 * - [UsbFault.NO_PERMISSION] is on while a dongle is present without permission, off once it is
 *   granted or the dongle is gone.
 */
object UsbPermissionPolicy {

    /** The service's scan or an attach saw [deviceName] (null = no dongle) with/without permission. */
    fun onScan(state: UsbPermissionState, deviceName: String?, granted: Boolean): UsbPermissionDecision {
        if (deviceName == null) return UsbPermissionDecision(request = false, state = UsbPermissionState())
        if (granted) return UsbPermissionDecision(request = false, state = state.copy(denied = false, fault = false))
        if (deviceName == state.requestedFor) {
            return UsbPermissionDecision(request = false, state = state.copy(fault = true))
        }
        return UsbPermissionDecision(
            request = true,
            state = UsbPermissionState(requestedFor = deviceName, denied = false, fault = true)
        )
    }

    /** The user asked for permission (header tap, notification action): ask whatever happened before. */
    fun onExplicitRequest(state: UsbPermissionState, deviceName: String?, granted: Boolean): UsbPermissionDecision {
        if (deviceName == null || granted) return onScan(state, deviceName, granted)
        return UsbPermissionDecision(
            request = true,
            state = UsbPermissionState(requestedFor = deviceName, denied = false, fault = true)
        )
    }

    fun onDenied(state: UsbPermissionState): UsbPermissionState = state.copy(denied = true)

    fun onGranted(state: UsbPermissionState): UsbPermissionState = state.copy(denied = false, fault = false)

    /** [deviceName] was detached (null = unknown): the appearance it belonged to is over. */
    fun onGone(state: UsbPermissionState, deviceName: String?): UsbPermissionState =
        if (deviceName == null || deviceName == state.requestedFor) UsbPermissionState() else state

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
