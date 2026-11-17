---
title: Реализовать исполнители: AlertCoordinator, окно, звук, уведомления
labels: [wayfinder:task]
status: closed
assignee: drhaos+claude
blocked_by: [11]
---

## Question

Код по [02](02-persistent-notification.md), [03](03-overlay-window.md), [04](04-alarm-sound.md), [06](06-alert-window-look.md), [08](08-alert-module-boundaries.md); подключение в сервис, удаление `AlertNotificationPolicy`/`Book`.

## Resolution

Сделано 2026-11-17 (код не собран: SDK в VM нет; сборка и тесты — на Windows). `AlertCoordinator`, `AlertOverlayController`, `AlertSoundPlayer` (звуки `res/raw` из `tools/alert_sounds/make_sounds.py`), `AlertNotifier` переписан (ongoing, перепубликация, FSI как запасной путь), `AlertNotificationReceiver` → координатор, канал `tpms_alerts_silent`, `MonitoringHealthNotifier` — только трекер, `ServiceHeartbeatStore` ключ `alert_presentation`, сервис: `ACTION_ACKNOWLEDGE`, SCREEN_ON/OFF, коллектор wheels/state; колбэки `onAlert`/`onSensorNormal` репозитория удалены; `FloatingOverlayController`: защищённый `addView`, без `TYPE_PHONE`. Отклонение от [08](08-alert-module-boundaries.md): повторная попытка загрузки состояния при сбое чтения не делается (до успешной загрузки состояние просто не пишется).
