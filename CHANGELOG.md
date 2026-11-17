# Changelog

All notable changes to TPMS Monitor. Versions follow the release tags (`vX.Y.Z`).

## [Unreleased]

### Design
- New main screen: an x-ray car on the road (rendered in Blender, `tools/car_render`), each tyre tinted by its state — green, amber, red, grey without data — with the running gear drawn in; pressure over temperature beside every wheel on its axle's line.
- Alert banner with what to do ("Stop in a safe place and check the tyre"); the header shows the warning state and the recommended pressure.
- New colour system and typography; dark, light or automatic appearance shared by the app and the widgets.
- Car widget redrawn in the same style (road, x-ray car, tinted tyres, readings); the compact widget and the notification use the app's colours.
- Notification small icon: the ISO TPMS telltale.

### Settings
- Restructured, autosaving settings without a Save button: Tyres, Sensors, Alerts, Display, System check, About.
- Recommended pressure: one target pressure with a stepper, or "accept current pressures as the norm"; low / high alert limits follow it (80 % / 135 %). The old absolute low limit is migrated; settings export v4.
- Sensors: swap the sensors of two wheels with one tap on the car picture (tyre rotation), or all four crosswise; per-wheel sensor choice by live pressure stays below.
- Sensor calibration: a per-sensor pressure offset (up to ±1 bar) set against an accurate gauge; it follows the sensor through a tyre rotation, applies to alerts, history and widgets, and is included in settings export v5 and the diagnostic report.
- Language: System / English / Русский (Android 13+ also in the system "App language" page); the APK ships only English and Russian resources.
- Expert options moved to a service menu, unlocked by tapping the version seven times.
- System check (service, battery, notifications, Teyes steps) on its own screen.

### Alerts
- Two alert levels: **Attention** (deviation, you can drive on — yellow) and **Fault** (do not drive — red). Fault: pressure below 65 % of the recommended, fast pressure loss (leak flag, −0.3 bar in 10 min, 0.4 bar below the other wheel of the axle), tyre at 100 °C (overheat limit + 15 °C), sensor lost, monitoring not working.
- Fault: a red window over any app (navigation, music, FrontApp) with the wheel, values and what to do, and a signal every 30 s until «Got it»; afterwards it comes back after a reminder interval (1–30 min, default 5). Worsening rings at once.
- Attention: a yellow banner with a short chime, once per alert type; «Mute 30 min» applies to attention only.
- Alert windows never close by themselves: the red one only with «Got it», the yellow one only with «×»; alerts that cleared meanwhile stay as "back to normal" lines.
- Alert notifications stay as long as the problem lasts (not swipeable; on Android 14+ a swiped one comes back silently); a fault has «Got it» in the notification and on the main screen.
- Signals are the app's own sounds at the alarm volume, ducking music; the alert notification channel is silent (channel settings reset once).
- Onboarding asks for "Display over other apps"; System check shows the alert window permission and the alarm volume; two test buttons in Settings → Alerts. Settings export v6 (reminder interval).
- One notification per wheel for real alerts only (low / high pressure, signal lost); each alert type rings once per episode, swiping keeps it hidden, "Mute 30 min" survives restarts; only live data rings.
- Debounced alerts: hysteresis on clear, leak flag held 15 s, battery warning latched 10 min.
- Alerts sound on the alarm stream (new "TPMS Alerts" channel; earlier custom channel settings are reset), category alarm; "Full-screen warning" now works: a ringing alert opens the app over the screen (needs "Display over other apps").
- A further pressure drop of 0.25 bar rings again, also after a swipe or a mute; high tyre temperature now gets a notification too.
- "Mute 30 min" silences only the wheels alerting at that moment (another wheel still rings) and is gone from the persistent notification.
- A new trip (wake after a sleep, or a start after the process was killed in it) rings ongoing alerts again.
- New: "Tyre pressure monitoring is not working" with sound when known wheels get no live data for 2 min with the screen on.
- New: signal lost for an HID sensor silent 10 min while the others transmit, and for an AA55 sensor whose value stays frozen 45 min while the others change; low pressure when a wheel is 0.4 bar below the other wheel of its axle.
- HID readings confirm within 3 min with a small tolerance, so a falling pressure is no longer held back.
- Settings → Alerts → "Test alert"; System check verifies the alert channel sound. Widgets show the clock time of the data instead of "N min ago".

### USB and background work
- Recovery from stuck USB readers and silent dongles (reopen, process restart, fault in the header).
- Wake on screen-on with suspend detection; diagnosis of a service killed during head-unit sleep.
- USB permission requested once per dongle appearance (with a grace period), quiet "no permission" notification; USB attach through an invisible trampoline activity.
- Wheel placement by receiver label or HID id with per-wheel mapping; AA55 empty slots; stale-data indication per wheel and per connection.
- Android 15: the monitoring service runs as a connected-device service, so it is no longer stopped after 6 hours a day.
- Sensor history in the database is kept for 30 days instead of growing without limit.

## [1.1.4a2] and earlier

See the [GitHub Releases](https://github.com/drrhaos/tpms/releases).
