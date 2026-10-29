---
title: Хронология от пробуждения магнитолы до первого кадра
labels: [wayfinder:task]
status: closed
assignee: agent-chronolog
blocked_by: []
---

## Question

Проследить по коду все пути после включения зажигания / выхода из сна / загрузки: `BootReceiver`, `BootStartScheduler`, `PowerEventReceiver` (SCREEN_ON → `ACTION_PROBE_USB`), `UsbAttachedReceiver`, `WakeEscalationScheduler`, `ServiceLivenessScheduler`, цикл опроса (`USB_CHECK_INTERVAL_MS`, `RECONNECT_DEBOUNCE_MS`, `DETACH_ABSENT_POLLS`, stale-frame 90 с), запрос USB-разрешения. AFK.

Результат: временная диаграмма лучшего/худшего случая до первого валидного кадра, где теряются секунды, какие сценарии вообще не восстанавливаются без действия пользователя (например, разрешение USB после re-enumeration).

## Resolution

Трассировка по коду, без железа. Время — до чтения с живого соединения; ко всему прибавляется `T_s` (период передачи датчика). Подробные цепочки с file:line: [assets/wake-timeline.md](assets/wake-timeline.md).

| Сценарий | Лучший | Худший | Само? |
|---|---|---|---|
| S1 холодная загрузка (от `BOOT_COMPLETED`) | ~1–2 с | ∞ без default association; +30/60/120 с при сбое старта FGS | только с association |
| S2a ACC on, процесс жив, новый путь, DETACHED дошёл | ~5–7 с | ~15–20 с; ∞ без разрешения | да* |
| S2b то же, DETACHED пропущен | ~5–10 с (сон > 90 с) | ~100 с (сон < 90 с); ∞ при зависшем HCD | нет при hang |
| S3 экран off/on без потери питания | 0 с | ~5–10 с (ложный stale после сна > 90 с) | да |
| S4 ручное переподключение | ~6–12 с | ∞ (диалог/chooser) | только с association |
| S5 процесс убит OEM | ~2–5 с (STICKY) | ~5 мин (liveness); ∞ при force-stop | частично |
| S6 разрешение потеряно после re-enumeration | ≤5 с после тапа | ∞ | **нет** |

\* только если разрешение пережило re-enumeration, то есть стоит «использовать по умолчанию».

**Главные потери времени:** (1) USB-разрешение — без default association бесконечно; (2) stale 90 с остаётся единственным детектором ситуации «тот же VID:PID, новый путь»; (3) liveness 5 мин / бэкофф STICKY; (4) watchdog 15–20 с × N на мёртвом соединении; (5) debounce 5 с после каждого закрытия; (6) USB_CHECK 5 с, когда перечисление ещё не закончилось; (7) итерация 2–5 с.

**Баги (главные; полный список B1–B15 в assets):**
- B1/B2 `AndroidManifest.xml:102-109`: `SCREEN_ON/OFF` в манифест-ресивер не доставляются, `registerReceiver` нет. Весь `WakeEscalationScheduler` и PROBE на пробуждение — мёртвый код. Если починить как есть, PROBE (`TpmsMonitorService.kt:102-107`) 6 раз за 2 мин рвёт здоровое соединение.
- B3 `TpmsMonitorService.kt:88-115,411-423`: `wake()/probeUsb()` идут через `startForegroundService`, но ветки CHECK_NOW/PROBE/DETACHED не вызывают `startForeground`. При неживом сервисе — краш через ~10 с (вызовы: `UsbAttachedReceiver.kt:52`, `UsbPermissionReceiver.kt:18`).
- B4 `UsbPermissionHelper.kt:39-44`: PendingIntent `FLAG_IMMUTABLE` → `EXTRA_PERMISSION_GRANTED` всегда false, `UsbPermissionReceiver` никогда не будит сервис.
- B5 `UsbConnection.kt:43-47` + `TpmsRepository.kt:246-249`: `isSameDevice` по VID:PID → после watchdog берётся старое мёртвое/висящее соединение.
- B6 `UsbConnection.kt:57-61`: «present» по VID:PID маскирует re-enumeration, `DETACH_ABSENT_POLLS` не срабатывает.
- B7 `TpmsMonitorService.kt:193-197`: сервис сам не запрашивает разрешение и не сообщает о его отсутствии.
- B10 `ServiceLivenessReceiver.kt:30` + `TpmsMonitorService.kt:125`: stale heartbeat при зависшем цикле → `start()` = no-op. Пути рестарта процесса нет.
- B12 `ConnectionHealthPolicy.kt:17-22`: stale по wall-clock рвёт здоровое соединение после сна > 90 с. B9: на 12+ неточные alarms (`setAndAllowWhileIdle`) не дают права стартовать FGS. B15: единственный путь без тапа (default association) заодно выводит `MainActivity` поверх экрана на каждом attach.

**Следствия для «стратегии реконнекта после пробуждения»** (можно оформлять тикетами):
- явный wake-триггер: динамический `SCREEN_ON` в сервисе и/или OEM-ACC broadcast;
- идентичность устройства — по `deviceName`, а не по VID:PID;
- при пробуждении безусловный reopen без debounce, со ступенчатым поиском (0,5/1/2/5 с) вместо фиксированных 5 с;
- политика разрешения: онбординг требует «по умолчанию», сервис показывает уведомление, `FLAG_MUTABLE`;
- `wake()` через `startService` или с `startForeground` во всех ветках;
- эскалация «reopen → рестарт процесса», когда reader не вернулся (связь с 07/08);
- stale-порог отдельно для «после открытия» и «после кадра».
