---
title: Как прервать зависший bulkTransfer на Android
labels: [wayfinder:research]
status: closed
assignee:
blocked_by: []
---

## Question

`withTimeout` вокруг блокирующего `UsbDeviceConnection.bulkTransfer` не прерывает его. Выяснить: прерывает ли `UsbDeviceConnection.close()` / `releaseInterface()` с другого потока ожидающий `bulkTransfer`; работает ли таймаут `bulkTransfer` надёжно на Android 9–13 (известные баги ядра/usbfs, особенно FYT/Teyes на Android 10); поведение `UsbRequest.queue` + `requestWait(timeout)` (API 26+) как неблокирующей альтернативы; как это делают `usb-serial-for-android` (mik3y) и подобные библиотеки.

Результат: факты с источниками + рекомендация, какой механизм надёжен.

## Assets

- Находки исследования: [docs/research/bulk-transfer-unblock.md](../../research/bulk-transfer-unblock.md)

## Resolution

Выводы по исходникам AOSP (android10) и ядра 4.14/5.10; на Teyes не проверено.

- `withTimeout` вокруг `bulkTransfer` **не работает**: отмена срабатывает только после выхода из нативного вызова. Ветка `close()` по таймауту при реальном зависании недостижима. Watchdog оставляет висеть поток из `Dispatchers.IO`, а `openDongle` при том же VID:PID снова берёт старое соединение. `probe()` и `write()` держат `usbLock` во время передачи.
- `UsbDeviceConnection.close()` **не будит** висящий `bulkTransfer` и даёт риск use-after-free и переиспользования fd.
- `releaseInterface()` **будит** его в штатном случае: URB завершаются с `-ESHUTDOWN`. Уверенность средняя-высокая.
- Таймаут `bulkTransfer` в норме надёжен, но ожидание непрерываемое. При зависшем хост-контроллере (после сна) поток навсегда остаётся в D-state, и из приложения это не лечится.
- `bulkTransfer(byte[])` держит `GetPrimitiveArrayCritical` всю передачу → при зависании может встать GC всего процесса (уверенность средняя).
- **Рекомендованный механизм:**
  - отдельный reader-поток с `UsbRequest.queue(direct ByteBuffer)` и бесконечным `requestWait()`, который прерываем и завершается при отключении устройства; кадры идут в корутину через канал с `withTimeoutOrNull`;
  - `requestWait(timeout)` не использовать: у mik3y он падал с SIGSEGV;
  - восстановление по шагам: `UsbRequest.cancel()` → `releaseInterface()` → `close()`, каждый шаг со своим дедлайном. Если поток чтения не вышел, перезапустить процесс сервиса;
  - watchdog должен переоткрывать соединение, а не только перезапускать корутину.

Подробности и источники: [docs/research/bulk-transfer-unblock.md](../../research/bulk-transfer-unblock.md).
