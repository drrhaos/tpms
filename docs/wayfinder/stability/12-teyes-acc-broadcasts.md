---
title: Какие сигналы шлют FYT/Teyes при включении зажигания
labels: [wayfinder:research]
status: closed
assignee: agent-researcher
blocked_by: []
---

## Question

Чтобы надёжно ловить пробуждение магнитолы, нужно знать, что система реально рассылает при ACC on/off и выходе из сна на FYT/Teyes (CC3, CC2L и др., Android 10+): OEM-broadcast'ы (`com.fyt.boot.ACCON`, `com.syu.*`, `android.intent.action.ACC_ON` и т.п.), доставляются ли они в манифестный receiver, приходит ли `BOOT_COMPLETED`/`QUICKBOOT_POWERON` после «быстрого сна», теряет ли USB-порт питание и переэнумерирует ли донгл (новый `deviceName`). Источники: 4pda/XDA темы FYT, декомпиляция FYT-сервисов, проекты автозапуска для FYT.

## Assets

- Находки: [docs/research/teyes-acc-broadcasts.md](../../research/teyes-acc-broadcasts.md)

## Resolution

Подробно: [docs/research/teyes-acc-broadcasts.md](../../research/teyes-acc-broadcasts.md).

- `com.fyt.boot.ACCON`/`ACCOFF` (а также `com.glsx.boot.*`, `ACTION_MT_COMMAND_SLEEP_OUT`) шлёт `com.syu.ms`, но это
  неявные broadcast'ы: на Android 8+ манифестный ресивер стороннего приложения их не получает (подтверждено авторами
  FytHWOneKey и FytFunctionalityExtender). Доставка в runtime-ресивер не подтверждена.
- `android.intent.action.ACC_ON` на FYT не найден. `com.microntek.*` относится к MTCD, не к Teyes.
- `BOOT_COMPLETED` приходит только при холодном старте (сон выключен, плановая перезагрузка примерно раз в 10 снов,
  3 суток стоянки). После suspend-to-RAM он не приходит, `QUICKBOOT_POWERON` на FYT не шлётся.
- `SCREEN_ON`/`SCREEN_OFF` приходят при каждом выходе из сна и уходе в сон, но только в динамический ресивер. Именно так
  wake детектит headunit-revived (порог «экран выключен > 60 с» → принудительная проверка USB).
- Сток `com.syu.ms` при сне вызывает `killAppWhenSleep()` и убивает сторонние приложения вне белого списка, на Teyes это
  лечится только NoKill. Поэтому процесс после сна часто мёртв (force-stop), и broadcast'ы ему не помогут.
  Выход — OEM-автозапуск после сна или FrontApp.
- USB: данные противоречивы (питание во сне «остаётся» против «порты мёртвые после сна»). `USB_DEVICE_ATTACHED`
  после hibernate может не прийти, поэтому при wake нужно самим пересканировать `deviceList` и сравнивать `deviceName`.

Рекомендация: runtime `SCREEN_ON`/`OFF` в сервисе + порог длительности → одна мягкая проверка USB. FYT ACC-интенты
добавить в `BootReceiver` только для логирования. Детектор suspend по скачку wall-clock против uptime. Онбординг Teyes
просит включить автозапуск после сна. Открытые вопросы (проверка на CC3) перечислены в research-файле.
