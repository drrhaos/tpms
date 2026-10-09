---
title: Как обрабатывать зависшее чтение USB
labels: [wayfinder:grilling]
status: open
assignee:
blocked_by: [06, 07]
---

## Question

При зависании `bulkTransfer` поток остаётся заблокированным, watchdog лишь перезапускает цикл и плодит заблокированные потоки. Какой механизм чтения и восстановления выбрать (выделенный reader-поток + close() извне, `UsbRequest` с `requestWait(timeout)`, переход на `usb-serial-for-android`), как его согласовать с watchdog и `WakeLock`, и что делать, если устройство не отвечает после переоткрытия.

## Подсказки

- Из [Кто единственный владелец чтения USB](06-usb-read-ownership.md): чтение живёт в `DongleSession` (reader-поток на `UsbRequest`); смерть сессии = DETACHED / нет в `deviceList` / ошибка / 15 с тишины. Нужно решить: как сессия умирает, если поток не выходит, эскалация до перезапуска процесса, порог тишины для HID.
