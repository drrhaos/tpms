package com.tpms.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbPermissionPolicyTest {

    private val dev1 = "/dev/bus/usb/001/002"
    private val dev2 = "/dev/bus/usb/001/003"

    private fun scan(state: UsbPermissionState, deviceName: String?, granted: Boolean = false) =
        UsbPermissionPolicy.onScan(state, deviceName, granted)

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
