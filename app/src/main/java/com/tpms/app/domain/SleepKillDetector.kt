package com.tpms.app.domain

/**
 * What the previous process left behind about its last service run. All times are wall clock.
 *
 * @property runStartedAtMs set when the service started monitoring and removed in `onDestroy`;
 *   non-null means that run ended without `onDestroy` (the process was killed).
 * @property runBootCount `Settings.Global.BOOT_COUNT` when that run started.
 * @property lastScreenOffMs / [lastScreenOnMs] the last `SCREEN_OFF` / `SCREEN_ON` any process saw.
 * @property lastProcessRestartMs when the app last killed itself on purpose (wayfinder 08).
 */
data class PreviousRun(
    val runStartedAtMs: Long?,
    val runBootCount: Int?,
    val lastScreenOffMs: Long?,
    val lastScreenOnMs: Long?,
    val lastProcessRestartMs: Long?
)

/**
 * Detects that the previous process was killed while the head unit slept (stock FYT
 * `killAppWhenSleep`), as opposed to a clean stop, our own restart or a reboot.
 */
object SleepKillDetector {

    /**
     * True only if the previous run:
     * - ended without `onDestroy`,
     * - was not ended by our own process restart (no restart recorded during that run),
     * - happened in this boot (a power cut during a long park is not a kill),
     * - saw a `SCREEN_OFF` and no `SCREEN_ON` after it, i.e. it was alive into the sleep and died there.
     *
     * Any missing data gives false.
     */
    fun killedDuringSleep(previous: PreviousRun, currentBootCount: Int?): Boolean {
        val startedAt = previous.runStartedAtMs ?: return false
        val screenOff = previous.lastScreenOffMs ?: return false
        val runBoot = previous.runBootCount ?: return false
        if (currentBootCount == null || currentBootCount != runBoot) return false
        val restart = previous.lastProcessRestartMs
        if (restart != null && restart >= startedAt) return false
        if (screenOff < startedAt) return false
        val screenOn = previous.lastScreenOnMs
        return screenOn == null || screenOn < screenOff
    }
}
