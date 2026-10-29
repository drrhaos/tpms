# Как прервать зависший `bulkTransfer` на Android

Исследование для тикета «Как прервать зависший bulkTransfer на Android».
Контекст: `data/usb/UsbConnection.kt` (чтение через `bulkTransfer(ep, buf, len, 3000)` внутри `withTimeout(3500)`),
`service/TpmsMonitorService.kt` (watchdog перезапускает корутину опроса при «зависании» > 15 с,
`ConnectionHealthPolicy.POLL_STUCK_MS`). Целевые устройства — Teyes/FYT на Android 10 (ядро обычно 4.14), плюс
универсальные Android 9–13.

Уровни уверенности: **высокая** — проверено по исходникам AOSP/ядра; **средняя** — вывод из исходников без проверки
на железе или подтверждено косвенно; **низкая** — отчёты пользователей/гипотеза.

---

## 0. Что сейчас происходит в приложении (анализ кода)

1. `withTimeout(timeoutMs + 500)` **не может сработать**, пока поток стоит в нативном `ioctl`. Отмена корутин
   кооперативная: `TimeoutCancellationException` бросается только после возврата из `bulkTransfer`. Значит ветка
   `catch (TimeoutCancellationException) { close() }` на практике недостижима для настоящего зависания
   (уверенность: высокая).
2. Watchdog делает `pollingJob.cancel()` + `startPolling()`. Заблокированный поток из `Dispatchers.IO` при этом
   остаётся висеть (утечка потока пула IO). USB-соединение watchdog не трогает.
3. Новый цикл вызывает `repository.openDongle()`, а тот при `isSameDevice(device)` сразу возвращает `true` — то есть
   переиспользует то же «зависшее» соединение и запускает **второй** `bulkTransfer` на тот же endpoint параллельно
   первому.
4. `probe()` и `write()` вызывают `bulkTransfer` **под `usbLock`**. Если завис именно `probe()`/`write()`, то `close()`
   из любого другого потока тоже заблокируется на `synchronized(usbLock)`. `read()` берёт снапшот под локом и
   выполняет передачу без него — это правильно.
5. `bulkTransfer(byte[])` в JNI держит массив через `GetPrimitiveArrayCritical` всё время, пока идёт `ioctl` (см. п. 1.3).
   Это побочный риск для GC всего процесса.

---

## 1. Разблокирует ли `close()` / `releaseInterface()` из другого потока висящий `bulkTransfer`?

### Как устроен вызов (уверенность: высокая)

- Java `UsbDeviceConnection.bulkTransfer(...)` → JNI `android_hardware_UsbDeviceConnection_bulk_request` →
  `usb_device_bulk_transfer()` из libusbhost → `ioctl(fd, USBDEVFS_BULK, {ep, len, timeout, data})`. Таймаут
  передаётся в ядро как есть, никакой пользовательской логики ожидания нет.
- В ядре `proc_bulk()` **отпускает `usb_lock_device`** на время передачи (`usb_unlock_device(dev); usb_bulk_msg(...);
  usb_lock_device(dev)`) — и в 4.14, и в 5.10. Поэтому другие ioctl на этом fd (в т.ч. `RELEASEINTERFACE`) не
  блокируются на мьютексе устройства.
- `usb_bulk_msg` → `usb_start_wait_urb()`: `wait_for_completion_timeout(&ctx.done, expire)`; по истечении —
  `usb_kill_urb(urb)` и возврат `-ETIMEDOUT`. Ожидание **непрерываемое** (TASK_UNINTERRUPTIBLE, D-state):
  сигналы и `Thread.interrupt()` на него не действуют. `timeout == 0` означает бесконечное ожидание
  (`MAX_SCHEDULE_TIMEOUT`).

### `UsbDeviceConnection.close()` — НЕ разблокирует (уверенность: высокая)

- JNI `close` → `usb_device_close()` → `close(device->fd); free(device);` и обнуление `mNativeContext`.
  Синхронизации в Java/JNI нет.
- В многопоточном процессе `ioctl` держит ссылку на `struct file`, поэтому `close(fd)` только убирает fd из таблицы;
  `usbdev_release()` (который освобождает интерфейсы и убивает async-URB) вызовется лишь после возврата висящего
  `ioctl`. Синхронный URB от `USBDEVFS_BULK` он бы всё равно не тронул.
- Риски: `free(device)` при параллельном вызове — гонка use-after-free на `device->fd` (поток мог прочитать указатель
  до `close`, но ещё не войти в `ioctl`). После `close()` номер fd может быть переиспользован новым `openDevice()`,
  и «опоздавший» поток выполнит ioctl на чужом fd. Окно маленькое, но реальное. Сам вставший в `ioctl` поток после
  возврата ничего из `device` не читает, так что падения после возврата не ожидается.

### `releaseInterface()` — ДА, в штатном случае разблокирует (уверенность: средняя-высокая, по исходникам ядра)

Цепочка в ядре: `USBDEVFS_RELEASEINTERFACE` → `releaseintf()` → `usb_driver_release_interface(&usbfs_driver, intf)`
→ `usb_unbind_interface()`. У `usbfs_driver` **нет `soft_unbind`**, поэтому выполняется
`usb_disable_interface(udev, intf, false)` → для каждого endpoint интерфейса `usb_disable_endpoint()` →
`usb_hcd_flush_endpoint()` → `unlink1(hcd, urb, -ESHUTDOWN)` для **всех** URB на endpoint, включая синхронный URB
от `USBDEVFS_BULK`. Ожидающий `wait_for_completion_timeout` просыпается, `bulkTransfer` возвращает `-1`.

Оговорки:
- Работает, только если хост-контроллер (HCD) корректно отдаёт unlink'нутый URB. `usb_hcd_flush_endpoint` сам ждёт,
  пока URB вернутся, поэтому при «мёртвом» HCD `releaseInterface()` повиснет вместе с читающим потоком.
- После этого интерфейс уже не захвачен — соединение надо полностью закрывать и открывать заново.
- Порядок восстановления: `releaseInterface()` (будит читателя) → дождаться возврата читающего потока (с таймаутом)
  → `close()`. Нынешний `closeLocked()` делает именно `releaseInterface` + `close`, но вызывается только из
  недостижимой ветки или из `open()` (а `openDongle` при том же устройстве `open()` не вызывает).
- Проверено чтением исходников ядра 4.14 и 5.10; на конкретном Teyes/UIS7862 не проверялось. Обязательно проверить
  логом: «release → bulkTransfer вернул -1 за N мс».

### Отключение устройства (уверенность: высокая)

При физическом отключении/снятии питания порта `usb_disconnect()` отключает все endpoint'ы, URB завершаются с
`-ESHUTDOWN/-ENODEV`, `bulkTransfer` сразу возвращает `-1`. Т.е. «устройство пропало» само по себе зависания
вызывать не должно — если HCD жив.

**Источники:**
- libusbhost (Android 10): https://android.googlesource.com/platform/system/core/+/refs/heads/android10-release/libusbhost/usbhost.c
- JNI: https://github.com/aosp-mirror/platform_frameworks_base/blob/android10-release/core/jni/android_hardware_UsbDeviceConnection.cpp
- Java: https://github.com/aosp-mirror/platform_frameworks_base/blob/android10-release/core/java/android/hardware/usb/UsbDeviceConnection.java
- Ядро usbfs: https://github.com/torvalds/linux/blob/v4.14/drivers/usb/core/devio.c (`proc_bulk`, `releaseintf`, `usbdev_release`, `reap_as`), https://github.com/torvalds/linux/blob/v5.10/drivers/usb/core/devio.c
- https://github.com/torvalds/linux/blob/v4.14/drivers/usb/core/message.c (`usb_start_wait_urb`, `usb_disable_endpoint`, `usb_disable_interface`)
- https://github.com/torvalds/linux/blob/v4.14/drivers/usb/core/driver.c (`usb_unbind_interface`, условие `soft_unbind`)
- https://github.com/torvalds/linux/blob/v4.14/drivers/usb/core/hcd.c (`usb_hcd_flush_endpoint`)

### 1.3. Побочный эффект: блокировка GC (уверенность: средняя)

JNI `bulk_request` берёт `GetPrimitiveArrayCritical(buffer)` **до** `ioctl` и отпускает после. В ART (Android 10,
CC-коллектор) для перемещаемого массива это `heap->IncrementDisableThreadFlip()`: GC не может сделать thread flip,
пока критическая секция открыта. При нормальной работе это паузы GC до величины таймаута (3 с), а при настоящем
зависании GC блокируется бесконечно. Тогда потоки, ждущие GC (аллокации, `GoToRunnable`), встают — процесс
«замерзает» целиком, вплоть до ANR и остановки watchdog-корутины. Это похоже на дамп из mik3y #266 (поток в
`GoToRunnable` после нативного вызова), но прямого подтверждения нет. `UsbRequest` с direct `ByteBuffer`
критическую секцию не открывает.

- ART `GetPrimitiveArrayCritical`: https://android.googlesource.com/platform/art/+/refs/heads/android10-release/runtime/jni/jni_internal.cc
- https://github.com/mik3y/usb-serial-for-android/issues/266

---

## 2. Надёжен ли таймаут `bulkTransfer` на Android 9–13?

- **В штатном случае надёжен** (уверенность: высокая). Таймаут целиком реализован в ядре
  (`wait_for_completion_timeout` + `usb_kill_urb`), одинаково в 4.14…5.10+. Android-слой его не трогает (только до
  Android 9 длина обрезалась до 16384 байт — для нас не важно). `-1` возвращается и при таймауте, и при ошибке, и при
  отключении — различить их можно только по прошедшему времени и контрольному запросу (так делает mik3y,
  `testConnection`: `controlTransfer GET_STATUS`, 200 мс).
- **Где он не спасает** (уверенность: средняя/низкая):
  1. **Неисправный/зависший хост-контроллер.** После таймаута `usb_kill_urb()` ждёт, пока HCD вернёт URB. Если HCD
     не отвечает (типичные сценарии: выход SoC из глубокого сна, «засыпание» порта, сбой после отключения во время
     unlink), поток навсегда остаётся в D-state внутри `usb_kill_urb`. Известный класс багов ядра («task hung in
     usb_kill_urb», syzkaller; «processes stuck in usb_kill_urb if unlink not answered before detach»). Из
     пользовательского пространства такой поток не прервать ничем; `releaseInterface`/`close` тоже повиснут или не
     помогут, процесс с D-state потоком может не завершиться даже по `kill`. Помогает только переподключение
     устройства/сброс питания порта/перезагрузка.
  2. **Головные устройства (FYT/Teyes)**: при ACC OFF/«быстром сне» вендор может отключать питание порта или
     усыплять контроллер. Если устройство честно отключилось — `bulkTransfer` вернёт `-1` (ок). Если контроллер
     усыплён «наполовину» — возможен сценарий п. 1. Публичных подтверждений именно для UIS7862 не нашёл
     (уверенность: низкая), но это совпадает с наблюдаемым поведением «зависло после пробуждения».
  3. **`timeout == 0`** — бесконечное ожидание (в нашем коде не используется, но важно при рефакторинге).
  4. Долгоработающие соединения иногда деградируют («USB get_status request failed» через несколько часов) —
     зависит от чипа (PL2303, некоторые CDC). Это не зависание, а отказ устройства; лечится переоткрытием/переподключением.
- mik3y отмечает потерю данных у `bulkTransfer` с короткими таймаутами при непрерывном потоке на высоких скоростях
  (для 19200 бод и редких кадров TPMS неактуально).

**Источники:**
- https://github.com/torvalds/linux/blob/v4.14/drivers/usb/core/message.c
- https://groups.google.com/g/syzkaller-bugs/c/WToHitujkhc/m/7exQe4ZMCgAJ (task hung in usb_kill_urb)
- https://docs.kernel.org/driver-api/usb/URB.html (семантика `usb_kill_urb`)
- https://github.com/mik3y/usb-serial-for-android/issues/503 (деградация через часы, `testConnection`)
- https://grokbase.com/t/gg/usb-serial-for-android/13bgyzq41c/bulktransfer-read-hangs-forever-with-timeout-200ms
- https://stackoverflow.com/questions/9108548/android-usb-host-bulktransfer-is-losing-data

---

## 3. `UsbRequest.queue(ByteBuffer)` + `requestWait(long)` (API 26+)

### Семантика (уверенность: высокая)

- `UsbRequest.initialize(conn, ep)` → `queue(buffer)` → `USBDEVFS_SUBMITURB` (асинхронный URB).
  Для не-direct буфера Android 8+ сам делает временный direct-буфер — критической секции JNI нет.
- `requestWait()` (без таймаута) → `USBDEVFS_REAPURB`. Ожидание в ядре (`reap_as`) — **TASK_INTERRUPTIBLE**, оно
  просыпается при завершении любого URB, при отключении устройства (`!connected(ps)` → `requestWait` вернёт `null`)
  и при сигнале. Это принципиально лучше синхронного `USBDEVFS_BULK` (D-state).
- `requestWait(long timeout)` (API 26): `poll(fd, POLLOUT, timeout)` + `USBDEVFS_REAPURBNDELAY` в цикле до дедлайна;
  по дедлайну бросается `java.util.concurrent.TimeoutException`. URB при этом **остаётся в очереди** — его можно
  дождаться следующим `requestWait` или отменить `cancel()`. `requestWait(0)` не гарантирует возврат уже
  завершённого запроса (из javadoc). Если `poll` вернул `POLLHUP/POLLERR` (отключение) — возвращается `null`.
- `UsbRequest.cancel()` → `USBDEVFS_DISCARDURB` → `usb_kill_urb()` для этого URB: он завершается с `-ENOENT`, а
  ожидающий `requestWait()` в другом потоке получает его и возвращается. **Это штатный способ прервать ожидание
  из другого потока.** Именно так `CommonUsbSerialPort.close()` у mik3y прерывает блокирующее чтение:
  `readRequest.cancel()` → `connection.close()`. В `SerialInputOutputManager.stop()` прямо написано:
  «when using readTimeout == 0 (default), additionally use usbSerialPort.close() to interrupt blocking read».
- `cancel()` не требует `usb_lock_device` надолго и не освобождает интерфейс. При мёртвом HCD `usb_kill_urb` внутри
  `cancel()` может повиснуть — поэтому вызывать его стоит из отдельного «одноразового» потока.

### Известные проблемы

- **SIGSEGV в `requestWait(timeout)` с короткими таймаутами** (уверенность: средняя — отчёт мейнтейнера mik3y):
  `Fatal signal 11 ... libusbhost.so (usb_request_wait+192) ... android_hardware_UsbDeviceConnection_request_wait`,
  «data loss / crashes were observed with timeout up to 200 msec». Поэтому mik3y для `timeout != 0` использует
  `bulkTransfer`, а `UsbRequest` — только с бесконечным `requestWait()`. Вероятная причина — гонки вокруг
  `REAPURBNDELAY`/переиспользования URB; точный баг AOSP не найден.
- `requestWait()` возвращает **любой** завершённый запрос на соединении (не только наш). Если на одном соединении
  крутится несколько `UsbRequest`, нужен разбор по `getClientData()`/ссылке. Синхронные `bulkTransfer`/
  `controlTransfer` в эту очередь не попадают — запись можно оставить синхронной.
- До API 26 `requestWait` без таймаута никогда не возвращался по времени (mik3y #159, Android bug 39522) — для
  minSdk 28 неактуально.
- Нельзя вызывать `UsbRequest.close()` для запроса, который ещё в очереди: сначала `cancel()`, дождаться его
  возврата из `requestWait` (или закрыть соединение), потом `close()`.
- Если `requestWait` вернул наш запрос с `position() == 0` — это может быть отмена, обрыв или пустой пакет; mik3y в
  этом случае делает `testConnection(true)` (GET_STATUS).
- mik3y PR #614: при тестах `SerialInputOutputManager.stop()` не возвращался, потому что `requestWait()` блокировал —
  подтверждение того, что без `cancel()`/`close()` бесконечный `requestWait` сам не выйдет.

### Пригодность для непрерывного чтения serial

Подходит, и это фактический стандарт: один (или несколько — mik3y `setReadQueue`) постоянно поставленный в очередь
`UsbRequest` на bulk-IN, отдельный поток с `requestWait()`, повторная постановка после каждого результата. Для
нашего трафика (19200 бод, кадры TPMS раз в секунды) хватит одного запроса размером `maxPacketSize` (64 байта).

**Источники:**
- https://developer.android.com/reference/android/hardware/usb/UsbDeviceConnection#requestWait(long)
- https://developer.android.com/reference/android/hardware/usb/UsbRequest
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android10-release/core/java/android/hardware/usb/UsbRequest.java
- usbhost.c (`usb_request_wait`, `usb_request_cancel`) — ссылка выше
- https://github.com/torvalds/linux/blob/v4.14/drivers/usb/core/devio.c (`reap_as`, `proc_unlinkurb`)
- https://github.com/mik3y/usb-serial-for-android/issues/159
- https://github.com/mik3y/usb-serial-for-android/issues/223
- https://github.com/mik3y/usb-serial-for-android/pull/614

---

## 4. Как это делают зрелые библиотеки

**mik3y/usb-serial-for-android** (`CommonUsbSerialPort`, `SerialInputOutputManager`):
- `read(dest, timeout != 0)` → `bulkTransfer` с таймаутом; при `-1` проверяет прошедшее время и, если вернулось
  раньше таймаута, делает `testConnection` (GET_STATUS, 200 мс) → `IOException("Connection lost")`.
- `read(dest, 0)` (по умолчанию в `SerialInputOutputManager`) → `UsbRequest.queue` + бесконечный `requestWait()`.
  Нулевой результат → `testConnection`.
- Прерывание: `close()` = `mReadRequest.cancel()` (+ отмена очереди запросов) → `closeInt()` →
  `connection.close()`. Отдельный поток чтения (`*_read`, приоритет `URGENT_AUDIO`), `stop()` асинхронный.
- Сознательно **не** использует `requestWait(timeout)` из-за SIGSEGV.
- Таймауты в `SerialInputOutputManager` — опциональны; мейнтейнер рекомендует бесконечное чтение + `close()` для
  остановки.

**Другие** (уверенность: средняя): `felHR85/UsbSerial` использует отдельный поток с `UsbRequest` + `requestWait()`
для асинхронного режима и `bulkTransfer` для синхронного; libusb на Linux/Android использует только асинхронные URB
(`SUBMITURB`/`REAPURBNDELAY` + `poll`) и реализует таймауты отменой (`DISCARDURB`), синхронного `USBDEVFS_BULK`
не использует вовсе — тот же принцип.

**Источники:**
- https://github.com/mik3y/usb-serial-for-android/blob/master/usbSerialForAndroid/src/main/java/com/hoho/android/usbserial/driver/CommonUsbSerialPort.java
- https://github.com/mik3y/usb-serial-for-android/blob/master/usbSerialForAndroid/src/main/java/com/hoho/android/usbserial/util/SerialInputOutputManager.java
- https://github.com/mik3y/usb-serial-for-android/issues/503, /issues/266, /issues/94

---

## 5. Рекомендация для TPMS Monitor

### Основной механизм чтения: `UsbRequest` + бесконечный `requestWait()` на выделенном потоке

1. Отдельный поток (не `Dispatchers.IO`), например `newSingleThreadExecutor` / `Thread("TPMS-usb-read")`, на каждое
   открытое соединение. Поток крутит: `request.queue(buf)` → `conn.requestWait()` → кадр в `Channel`/`SharedFlow`
   → снова `queue`. Опрос в сервисе читает из канала с `withTimeoutOrNull` — корутина больше никогда не блокируется
   в нативном коде, и watchdog видит реальное состояние.
2. Direct `ByteBuffer` размером `maxPacketSize` (64): нет `GetPrimitiveArrayCritical`, нет риска заморозить GC.
3. `requestWait()` вернул `null` или ноль байт → `controlTransfer GET_STATUS` (200 мс); провал → считаем
   устройство потерянным, переоткрываем.
4. **Не использовать `requestWait(timeout)`** (SIGSEGV у mik3y) — таймаут «нет данных» считать на стороне корутины
   (уже есть `shouldReconnectStaleFrame`).
5. Запись (команды CH340/AA55) оставить синхронным `bulkTransfer` с коротким таймаутом, но **без удержания
   `usbLock`** во время передачи (то же для `probe()`; лучше заменить `probe()` на `controlTransfer GET_STATUS`).
6. HID: для interrupt-IN `UsbRequest` работает так же (это штатный путь для interrupt endpoint'ов).

### Восстановление (watchdog / отключение / stale frame)

Порядок, каждый шаг из отдельного потока с собственным дедлайном:
1. `readRequest.cancel()` (DISCARDURB) — штатно будит `requestWait()`.
2. Если поток чтения не вышел за ~1 с: `releaseInterface()` на захваченных интерфейсах (по исходникам ядра флашит
   endpoint и будит и async, и синхронные URB).
3. `connection.close()`; `UsbRequest.close()` — только после того, как поток чтения завершился (иначе возможен
   use-after-free буфера).
4. Если поток чтения не вышел и после шага 3 за ~3–5 с — он в D-state в ядре (мёртвый HCD). Восстановление внутри
   процесса невозможно: зафиксировать в `serviceHealth`, забросить поток, перейти к переоткрытию; при повторении —
   перезапуск процесса сервиса (через существующий `ServiceLivenessScheduler`/AlarmManager) и сообщение
   пользователю о переподключении донгла. Не создавать новые потоки чтения бесконечно (ограничить счётчиком
   «осиротевших» потоков).
5. Watchdog должен не только перезапускать корутину, но и инициировать эту процедуру и **принудительно
   переоткрывать** соединение (сейчас `openDongle` при том же VID:PID переиспользует старое соединение).

### Минимальная правка, если переход на `UsbRequest` откладывается

- Убрать иллюзию `withTimeout` вокруг `bulkTransfer` (она не работает) и выполнять чтение на выделенном потоке;
  корутина ждёт результат через `withTimeoutOrNull` и при превышении вызывает `releaseInterface()` + `close()` из
  другого потока.
- Не держать `usbLock` во время `bulkTransfer` в `probe()`/`write()`.
- Watchdog: вызывать `closeUsbForReconnect()` до перезапуска опроса.

### Что проверить на устройстве

1. Логировать длительность каждого `bulkTransfer`/`requestWait` и что стало причиной возврата.
2. Эксперимент: при активном чтении вызвать `releaseInterface()` / `cancel()` из другого потока — убедиться, что
   чтение возвращается за < 100 мс на Teyes (UIS7862, ядро 4.14).
3. Сценарий ACC OFF → сон → пробуждение: снять `adb shell ps -t -o stat,wchan` (или `cat /proc/<pid>/task/*/wchan`
   при доступе) для зависшего потока — `usb_kill_urb`/`usb_start_wait_urb` в D-state подтвердит сценарий «мёртвый HCD».
