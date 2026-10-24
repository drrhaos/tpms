package com.tpms.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuspendDetectorTest {

    private var sleptMs = 0L
    private val detector = SuspendDetector(sleepClock = { sleptMs })

    @Test
    fun screenOn_withoutScreenOff_isNotSuspend() {
        sleptMs = 60_000L
        assertFalse(detector.onScreenOn())
    }

    @Test
    fun sleepBelowThreshold_isNotSuspend() {
        sleptMs = 1_000L
        detector.onScreenOff()
        sleptMs += SuspendDetector.SUSPEND_THRESHOLD_MS - 1
        assertFalse(detector.onScreenOn())
    }

    @Test
    fun sleepAtThreshold_isSuspend() {
        sleptMs = 1_000L
        detector.onScreenOff()
        sleptMs += SuspendDetector.SUSPEND_THRESHOLD_MS
        assertTrue(detector.onScreenOn())
    }

    @Test
    fun longSleep_isSuspend() {
        detector.onScreenOff()
        sleptMs += 3_600_000L
        assertTrue(detector.onScreenOn())
    }

    @Test
    fun screenOn_consumesBaseline() {
        detector.onScreenOff()
        sleptMs += 10_000L
        assertTrue(detector.onScreenOn())
        sleptMs += 10_000L
        assertFalse(detector.onScreenOn())
    }

    @Test
    fun repeatedScreenOff_usesLatestBaseline() {
        detector.onScreenOff()
        sleptMs += 10_000L
        detector.onScreenOff()
        assertFalse(detector.onScreenOn())
    }
}
