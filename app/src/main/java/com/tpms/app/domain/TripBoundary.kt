package com.tpms.app.domain

/**
 * Where a new trip begins, so alerts still active from the last one ring again
 * ([com.tpms.app.service.AlertNotifier.onNewTrip]): a flat tyre found in the morning must not be a
 * silent update of yesterday's episode. All times are wall clock.
 */
object TripBoundary {

    /** A screen-off at least this long without a real suspend still counts as a parked car. */
    const val MIN_SCREEN_OFF_MS = 10 * 60_000L

    /**
     * SCREEN_ON: a new trip if the head unit really suspended, or the screen was off for at least
     * [MIN_SCREEN_OFF_MS] ([screenOffAtMs] null = unknown, not long enough).
     */
    fun startsOnScreenOn(suspended: Boolean, screenOffAtMs: Long?, nowMs: Long): Boolean =
        suspended || (screenOffAtMs != null && nowMs - screenOffAtMs >= MIN_SCREEN_OFF_MS)

    /**
     * Service process start with the screen on: a new trip after a reboot, or when the last screen
     * event any process saw was a SCREEN_OFF (the process died in the sleep and missed its end).
     * With the screen off nothing is decided here: the coming SCREEN_ON does it.
     */
    fun startsOnProcessStart(previous: PreviousRun?, currentBootCount: Int?, screenOn: Boolean): Boolean {
        if (!screenOn || previous == null) return false
        val runBoot = previous.runBootCount
        if (currentBootCount != null && runBoot != null && currentBootCount != runBoot) return true
        val off = previous.lastScreenOffMs ?: return false
        val on = previous.lastScreenOnMs
        return on == null || on < off
    }
}
