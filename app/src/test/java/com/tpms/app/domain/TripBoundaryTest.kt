package com.tpms.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripBoundaryTest {

    private val now = 10_000_000L

    private fun run(off: Long?, on: Long?, boot: Int? = 7) =
        PreviousRun(runStartedAtMs = null, runBootCount = boot, lastScreenOffMs = off, lastScreenOnMs = on, lastProcessRestartMs = null)

    @Test
    fun `a real suspend starts a trip`() {
        assertTrue(TripBoundary.startsOnScreenOn(suspended = true, screenOffAtMs = now - 1_000, nowMs = now))
    }

    @Test
    fun `a long screen-off starts a trip, a short one does not`() {
        assertTrue(TripBoundary.startsOnScreenOn(false, now - TripBoundary.MIN_SCREEN_OFF_MS, now))
        assertFalse(TripBoundary.startsOnScreenOn(false, now - TripBoundary.MIN_SCREEN_OFF_MS + 1, now))
        assertFalse(TripBoundary.startsOnScreenOn(false, null, now))
    }

    @Test
    fun `process start after a sleep it did not see end starts a trip`() {
        assertTrue(TripBoundary.startsOnProcessStart(run(off = now - 5_000, on = now - 60_000), 7, screenOn = true))
        assertTrue(TripBoundary.startsOnProcessStart(run(off = now - 5_000, on = null), 7, screenOn = true))
    }

    @Test
    fun `process restart while driving is the same trip`() {
        assertFalse(TripBoundary.startsOnProcessStart(run(off = now - 60_000, on = now - 5_000), 7, screenOn = true))
        assertFalse(TripBoundary.startsOnProcessStart(run(off = null, on = null), 7, screenOn = true))
    }

    @Test
    fun `a reboot starts a trip`() {
        assertTrue(TripBoundary.startsOnProcessStart(run(off = now - 60_000, on = now - 5_000, boot = 6), 7, screenOn = true))
    }

    @Test
    fun `screen off at start leaves the decision to SCREEN_ON`() {
        assertFalse(TripBoundary.startsOnProcessStart(run(off = now - 5_000, on = null), 7, screenOn = false))
        assertFalse(TripBoundary.startsOnProcessStart(null, 7, screenOn = true))
    }
}
