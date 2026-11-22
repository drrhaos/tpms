package com.tpms.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlertSoundRouteTest {

    @Test
    fun teyesDefaultsToMedia() {
        assertEquals(AlertSoundRoute.MEDIA, AlertSoundRoute.effective(null, teyesHeadUnit = true))
    }

    @Test
    fun otherDevicesDefaultToAlarm() {
        assertEquals(AlertSoundRoute.ALARM, AlertSoundRoute.effective(null, teyesHeadUnit = false))
    }

    @Test
    fun userChoiceWins() {
        assertEquals(AlertSoundRoute.NAVIGATION, AlertSoundRoute.effective(AlertSoundRoute.NAVIGATION, teyesHeadUnit = true))
        assertEquals(AlertSoundRoute.ALARM, AlertSoundRoute.effective(AlertSoundRoute.ALARM, teyesHeadUnit = true))
    }

    @Test
    fun unknownNameIsNoChoice() {
        assertNull(AlertSoundRoute.fromName("LOUD"))
        assertNull(AlertSoundRoute.fromName(null))
        assertEquals(AlertSoundRoute.MEDIA, AlertSoundRoute.fromName("MEDIA"))
    }
}
