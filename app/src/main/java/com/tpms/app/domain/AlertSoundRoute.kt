package com.tpms.app.domain

/**
 * Which Android audio stream carries the alert signals (drhaos, 2026-11-22: no sound on Teyes).
 *
 * FYT/Teyes routes sound through its MCU: the alarm stream may never reach the speakers there,
 * while the media stream always does when Android plays (research «alarm-sound», items 14–16).
 * - [ALARM] — alarm volume, passes "alarms only" DND; the default on phones and tablets.
 * - [NAVIGATION] — navigation guidance: FYT may mix it over the radio like a navigator's voice.
 * - [MEDIA] — media volume (the volume knob on a head unit); the default on Teyes.
 */
enum class AlertSoundRoute {
    ALARM, NAVIGATION, MEDIA;

    companion object {
        /** The stored choice, or the device's default when none was made. */
        fun effective(stored: AlertSoundRoute?, teyesHeadUnit: Boolean): AlertSoundRoute =
            stored ?: if (teyesHeadUnit) MEDIA else ALARM

        fun fromName(name: String?): AlertSoundRoute? = entries.firstOrNull { it.name == name }
    }
}
