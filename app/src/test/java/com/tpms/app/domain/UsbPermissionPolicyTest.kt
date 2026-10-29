package com.tpms.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbPermissionPolicyTest {

    private val dev1 = "/dev/bus/usb/001/002"
    private val dev2 = "/dev/bus/usb/001/003"

    private val grace = UsbPermissionPolicy.AUTO_REQUEST_GRACE_MS

    /** Scan [deviceName] first at [firstMs], then again once the grace period is over: the automatic request is due. */
    private fun scan(state: UsbPermissionState, deviceName: String?, granted: Boolean = false, firstMs: Long = 0L) =
        UsbPermissionPolicy.onScan(
            UsbPermissionPolicy.onScan(state, deviceName, granted, firstMs).state,
            deviceName,
            granted,
            firstMs + grace
        )

    private fun scanAt(state: UsbPermissionState, deviceName: String?, nowMs: Long, granted: Boolean = false) =
        UsbPermissionPolicy.onScan(state, deviceName, granted, nowMs)

    // --- Automatic request: once per appearance ---

    @Test
    fun noDongle_noRequestNoFault() {
        val decision = scan(UsbPermissionState(), null)
        assertFalse(decision.request)
        assertFalse(decision.state.fault)
    }

    @Test
    fun dongleWithPermission_noRequestNoFault() {
        val decision = scan(UsbPermissionState(), dev1, granted = true)
        assertFalse(decision.request)
        assertFalse(decision.state.fault)
    }

    @Test
    fun dongleWithoutPermission_requestsOnceAndRaisesFault() {
        val first = scan(UsbPermissionState(), dev1)
        assertTrue(first.request)
        assertTrue(first.state.fault)
        assertEquals(dev1, first.state.requestedFor)

        val second = scan(first.state, dev1)
        assertFalse(second.request)
        assertTrue(second.state.fault)
    }

    @Test
    fun afterDenial_sameAppearanceIsNotAskedAgain() {
        val asked = scan(UsbPermissionState(), dev1).state
        val denied = UsbPermissionPolicy.onDenied(asked)
        assertTrue(denied.denied)
        assertTrue(denied.fault)

        val again = scan(denied, dev1)
        assertFalse(again.request)
        assertTrue(again.state.fault)
    }

    @Test
    fun newDeviceName_isNewAppearance() {
        val denied = UsbPermissionPolicy.onDenied(scan(UsbPermissionState(), dev1).state)
        val decision = scan(denied, dev2)
        assertTrue(decision.request)
        assertFalse(decision.state.denied)
        assertEquals(dev2, decision.state.requestedFor)
    }

    @Test
    fun dongleGoneAndBack_isNewAppearance() {
        val denied = UsbPermissionPolicy.onDenied(scan(UsbPermissionState(), dev1).state)
        val gone = scan(denied, null)
        assertFalse(gone.request)
        assertFalse(gone.state.fault)
        assertEquals(UsbPermissionState(), gone.state)

        val back = scan(gone.state, dev1)
        assertTrue(back.request)
        assertTrue(back.state.fault)
    }

    @Test
    fun detachOfRequestedDongle_resets() {
        val asked = scan(UsbPermissionState(), dev1).state
        assertEquals(UsbPermissionState(), UsbPermissionPolicy.onGone(asked, dev1))
        assertEquals(UsbPermissionState(), UsbPermissionPolicy.onGone(asked, null))
    }

    @Test
    fun detachOfAnotherDevice_keepsState() {
        val asked = scan(UsbPermissionState(), dev1).state
        assertEquals(asked, UsbPermissionPolicy.onGone(asked, dev2))
    }

    // --- Grace period: the default-association grant may land after the attach ---

    @Test
    fun firstSight_waitsForGracePeriod() {
        val first = scanAt(UsbPermissionState(), dev1, nowMs = 1_000L)
        assertFalse(first.request)
        assertFalse(first.state.fault)
        assertEquals(dev1, first.state.firstSeenFor)
        assertEquals(1_000L, first.state.firstSeenMs)
        assertEquals(grace, first.recheckAfterMs)
    }

    @Test
    fun withinGracePeriod_noRequestAndFirstSightKept() {
        val first = scanAt(UsbPermissionState(), dev1, nowMs = 1_000L).state
        val early = scanAt(first, dev1, nowMs = 1_000L + grace - 1)
        assertFalse(early.request)
        assertFalse(early.state.fault)
        assertEquals(1_000L, early.state.firstSeenMs)
        assertEquals(1L, early.recheckAfterMs)
    }

    @Test
    fun afterGracePeriod_requestsAndRaisesFault() {
        val first = scanAt(UsbPermissionState(), dev1, nowMs = 1_000L).state
        val due = scanAt(first, dev1, nowMs = 1_000L + grace)
        assertTrue(due.request)
        assertTrue(due.state.fault)
        assertEquals(dev1, due.state.requestedFor)
        assertNull(due.recheckAfterMs)
    }

    @Test
    fun defaultGrantWithinGracePeriod_noRequestNoFault() {
        val first = scanAt(UsbPermissionState(), dev1, nowMs = 1_000L).state
        val granted = scanAt(first, dev1, nowMs = 1_500L, granted = true)
        assertFalse(granted.request)
        assertFalse(granted.state.fault)
        assertNull(granted.recheckAfterMs)
    }

    @Test
    fun newDeviceNameWithinGracePeriod_restartsGracePeriod() {
        val first = scanAt(UsbPermissionState(), dev1, nowMs = 1_000L).state
        val other = scanAt(first, dev2, nowMs = 1_000L + grace)
        assertFalse(other.request)
        assertEquals(dev2, other.state.firstSeenFor)
        assertEquals(1_000L + grace, other.state.firstSeenMs)
    }

    @Test
    fun dongleGoneWithinGracePeriod_restartsGracePeriodOnReturn() {
        val first = scanAt(UsbPermissionState(), dev1, nowMs = 1_000L).state
        val gone = UsbPermissionPolicy.onGone(first, dev1)
        assertEquals(UsbPermissionState(), gone)
        assertFalse(scanAt(gone, dev1, nowMs = 1_000L + grace).request)
    }

    @Test
    fun explicitRequest_ignoresGracePeriod() {
        val first = scanAt(UsbPermissionState(), dev1, nowMs = 1_000L).state
        val explicit = UsbPermissionPolicy.onExplicitRequest(first, dev1, granted = false)
        assertTrue(explicit.request)
        assertTrue(explicit.state.fault)
        assertFalse(scanAt(explicit.state, dev1, nowMs = 1_000L + grace).request)
    }

    // --- Grant ---

    @Test
    fun grant_clearsFault() {
        val denied = UsbPermissionPolicy.onDenied(scan(UsbPermissionState(), dev1).state)
        val granted = UsbPermissionPolicy.onGranted(denied)
        assertFalse(granted.fault)
        assertFalse(granted.denied)
    }

    @Test
    fun scanWithPermission_clearsFaultWithoutNewRequest() {
        val asked = scan(UsbPermissionState(), dev1).state
        val decision = scan(asked, dev1, granted = true)
        assertFalse(decision.request)
        assertFalse(decision.state.fault)
    }

    // --- Explicit (user) request ---

    @Test
    fun explicitRequest_asksEvenAfterDenial() {
        val denied = UsbPermissionPolicy.onDenied(scan(UsbPermissionState(), dev1).state)
        val decision = UsbPermissionPolicy.onExplicitRequest(denied, dev1, granted = false)
        assertTrue(decision.request)
        assertFalse(decision.state.denied)
        assertTrue(decision.state.fault)
    }

    @Test
    fun explicitRequest_countsAsThisAppearancesRequest() {
        val explicit = UsbPermissionPolicy.onExplicitRequest(UsbPermissionState(), dev1, granted = false)
        assertTrue(explicit.request)
        assertFalse(scan(explicit.state, dev1).request)
    }

    @Test
    fun explicitRequest_withoutDongleOrWithPermission_doesNotAsk() {
        assertFalse(UsbPermissionPolicy.onExplicitRequest(UsbPermissionState(), null, granted = false).request)
        val granted = UsbPermissionPolicy.onExplicitRequest(UsbPermissionState(), dev1, granted = true)
        assertFalse(granted.request)
        assertFalse(granted.state.fault)
    }

    // --- Published fault ---

    @Test
    fun publishedFault_stuckWinsOverMissingPermission() {
        assertEquals(UsbFault.USB_STUCK, UsbPermissionPolicy.publishedFault(UsbFault.USB_STUCK, permissionFault = true))
    }

    @Test
    fun publishedFault_missingPermissionWinsOverSilentDongle() {
        assertEquals(UsbFault.NO_PERMISSION, UsbPermissionPolicy.publishedFault(UsbFault.DONGLE_SILENT, permissionFault = true))
        assertEquals(UsbFault.NO_PERMISSION, UsbPermissionPolicy.publishedFault(null, permissionFault = true))
    }

    @Test
    fun publishedFault_withoutPermissionFault_isRecoveryFault() {
        assertEquals(UsbFault.DONGLE_SILENT, UsbPermissionPolicy.publishedFault(UsbFault.DONGLE_SILENT, permissionFault = false))
        assertNull(UsbPermissionPolicy.publishedFault(null, permissionFault = false))
    }
}
