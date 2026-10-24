package com.tpms.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepKillDetectorTest {

    private val boot = 7

    /** Started at 1000, screen on at 2000, screen off at 5000, then nothing: killed in the sleep. */
    private val killedInSleep = PreviousRun(
        runStartedAtMs = 1_000L,
        runBootCount = boot,
        lastScreenOffMs = 5_000L,
        lastScreenOnMs = 2_000L,
        lastProcessRestartMs = null
    )

    private fun detect(previous: PreviousRun, currentBoot: Int? = boot) =
        SleepKillDetector.killedDuringSleep(previous, currentBoot)

    @Test
    fun runKilledAfterScreenOff_isSleepKill() {
        assertTrue(detect(killedInSleep))
    }

    @Test
    fun noScreenOnEverRecorded_isSleepKill() {
        assertTrue(detect(killedInSleep.copy(lastScreenOnMs = null)))
    }

    @Test
    fun cleanShutdown_isNotSleepKill() {
        assertFalse(detect(killedInSleep.copy(runStartedAtMs = null)))
    }

    @Test
    fun ourRestartDuringThatRun_isNotSleepKill() {
        assertFalse(detect(killedInSleep.copy(lastProcessRestartMs = 6_000L)))
    }

    @Test
    fun ourRestartBeforeThatRun_doesNotHide() {
        assertTrue(detect(killedInSleep.copy(lastProcessRestartMs = 500L)))
    }

    @Test
    fun screenOnAfterScreenOff_isNotSleepKill() {
        // Survived the sleep and died later with the screen on.
        assertFalse(detect(killedInSleep.copy(lastScreenOnMs = 8_000L)))
    }

    @Test
    fun screenOffBeforeThatRun_isNotSleepKill() {
        // The SCREEN_OFF was seen by an older process; this run never went to sleep.
        assertFalse(detect(killedInSleep.copy(lastScreenOffMs = 800L, lastScreenOnMs = 900L)))
        assertFalse(detect(killedInSleep.copy(lastScreenOffMs = 800L, lastScreenOnMs = null)))
    }

    @Test
    fun reboot_isNotSleepKill() {
        assertFalse(detect(killedInSleep, currentBoot = boot + 1))
    }

    @Test
    fun missingScreenOff_isNotSleepKill() {
        assertFalse(detect(killedInSleep.copy(lastScreenOffMs = null)))
    }

    @Test
    fun missingBootCount_isNotSleepKill() {
        assertFalse(detect(killedInSleep.copy(runBootCount = null)))
        assertFalse(detect(killedInSleep, currentBoot = null))
    }
}
