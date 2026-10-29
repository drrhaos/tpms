package com.tpms.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DongleProtocolTest {

    @Test
    fun streamsContinuously_onlyForSlotCacheReceivers() {
        assertFalse(DongleProtocol.HID_GENERIC.streamsContinuously)
        assertTrue(DongleProtocol.SERIAL_AA55.streamsContinuously)
        assertTrue(DongleProtocol.DEELIFE.streamsContinuously)
    }

    @Test
    fun fromNameOrNull_roundTripsEveryProtocol() {
        DongleProtocol.entries.forEach { assertEquals(it, DongleProtocol.fromNameOrNull(it.name)) }
    }

    @Test
    fun fromNameOrNull_nullForMissingOrUnknown() {
        assertNull(DongleProtocol.fromNameOrNull(null))
        assertNull(DongleProtocol.fromNameOrNull(""))
        assertNull(DongleProtocol.fromNameOrNull("USB_SOMETHING"))
    }
}
