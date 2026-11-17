---
title: Какие модули реализуют двухуровневые тревоги
labels: [wayfinder:grilling]
status: closed
assignee: drhaos+claude
blocked_by: []
---

## Question

Решения [01](01-severity-classification.md), [02](02-persistent-notification.md), [03](03-overlay-window.md), [04](04-alarm-sound.md), [05](05-alert-lifecycle.md) задают поведение. Как разложить его по модулям, чтобы логика была чистой и тестируемой, а Android-исполнители — тонкими?

- Чистая политика: уровни (`AlertLevel`, `PRESSURE_DROP`, детектор скорости падения — где живёт его история), жизненный цикл (подтверждение, напоминания, ухудшение, «Без звука», новая поездка, пауза при выключенном экране) — один модуль или несколько; что из `AlertNotificationPolicy` / `AlertNotificationBook` / `MonitoringHealthPolicy` переиспользуется, что удаляется.
- Исполнители: уведомления (`AlertNotifier`), окно (`AlertOverlayController`), звук (`AlertSoundPlayer`), плашка на главном экране — кто их вызывает, кто владеет таймерами (30 с, интервал напоминаний, 10 с жёлтого окна).
- Состояние вне процесса (`ServiceHeartbeatStore`): формат, миграция с текущей памяти уведомлений.
- Порядок реализации (нарезка на task-тикеты).

Скиллы: `grilling`, `codebase-design`.

## Resolution

Решено агентом по поручению drhaos («выполни все сам»), 2026-11-17 (`codebase-design`: глубокие чистые модули, тонкие исполнители).

- **Уровни — в существующих чистых модулях.** `AlertLevel { ATTENTION, FAULT }`; `TireSensor.alertLevel`. `AlertEvaluator` считает тип и уровень (критически низкое, перегрев «Неисправности», флаг утечки → `PRESSURE_DROP`, скорость падения); история давления для скорости — в `AlertMemory` (образцы за 10 мин, не чаще 1 на 30 с). `WheelCrossCheck`: перекос на оси → `PRESSURE_DROP`/FAULT, потеря датчика → `SENSOR_LOST`/FAULT. `AlertSeverity` остаётся только как цвет UI и выводится из уровня (ATTENTION → WARNING, FAULT → ALERT) — так меньше правок в UI при том же смысле.
- **Жизненный цикл — новый чистый модуль `AlertPresentation`** (domain) вместо `AlertNotificationPolicy` / `AlertNotificationBook`: вход — текущие тревоги колёс (id, слот, тип, уровень, давление), «мониторинг не работает», экран вкл/выкл, время, настройки (интервал напоминаний, «Без звука»); память — по ключу (эпизод, показанные типы «Внимания», «Понятно», база эскалации, время следующего напоминания) + JSON; выход — план: содержимое красного окна / жёлтый баннер / ничего, звук (FAULT / ATTENTION / нет), уведомления (ключ → уровень, текст), время следующего пересчёта. Тестируется целиком без Android.
- **Исполнители:** `AlertCoordinator` (`@Singleton`, главный поток) — единственный владелец таймеров (handler на `nextCheckAtMs`), кормит `AlertPresentation` из `repository.wheels` / blind-состояния / SCREEN_ON/OFF / действий пользователя, исполняет план через `AlertNotifier` (только уведомления: ongoing, тихая перепубликация, FSI, действие «Понятно»), `AlertOverlayController` (красное окно и жёлтый баннер, `TYPE_APPLICATION_OVERLAY`), `AlertSoundPlayer` (`USAGE_ALARM`, `MAY_DUCK`, свои WAV). `MonitoringHealthNotifier` больше не постит сам — даёт только blind-состояние.
- **Состояние вне процесса:** `ServiceHeartbeatStore` ключ `alert_presentation` (JSON); старый `alert_notifications` не читается (одноразовый сброс памяти уведомлений при обновлении).
- **Нарезка:** [10](10-impl-alert-levels.md) → [11](11-impl-alert-presentation.md) → [12](12-impl-alert-executors.md) → [13](13-impl-alert-ui.md) → [14](14-teyes-checklist.md).
