# Хронология: от пробуждения магнитолы до первого валидного кадра

Приложение к тикету [09-wake-timeline](../09-wake-timeline.md). Только чтение кода (сборки и железа нет).
Пути — от `app/src/main/java/com/tpms/app/`. «Первый кадр» = `TpmsRepository.readSensor()` получил непустой
`parse()` → `lastValidFrameAtMs` (`data/repository/TpmsRepository.kt:281-282`) → `applySensorUpdate`.

`T_s` — задержка самого датчика/донгла (период передачи датчика; на стоянке может быть десятки секунд и больше).
Она добавляется ко всем сценариям, код на неё не влияет. Ниже время указано **для приложения** (до момента, когда
идёт чтение с живого соединения), затем `+ T_s`.

## Константы

| Константа | Значение | Где |
|---|---|---|
| `POLL_INTERVAL_MS` | 2 с (пауза после каждой итерации внутреннего цикла) | `service/TpmsMonitorService.kt:393`, `:239` |
| `READ_TIMEOUT_MS` (+ запас 0,5 с) | 3 с | `data/usb/UsbConnection.kt:373-374` |
| → одна итерация внутреннего цикла | 2…5 с | `read` до 3 с + `delay` 2 с |
| `USB_CHECK_INTERVAL_MS` | 5 с (нет донгла / нет разрешения / не открылся) | `TpmsMonitorService.kt:394`, `:189,195,201` |
| `RECONNECT_DEBOUNCE_MS` | 5 с после **любого** выхода из внутреннего цикла | `TpmsMonitorService.kt:395`, `:243-244` |
| `DETACH_ABSENT_POLLS` | 3 итерации «нет в списке» + `probe()` | `TpmsMonitorService.kt:396`, `:219-231` |
| stale frame | 90 с от `max(lastValidFrameAt, dongleOpenedAt)` | `domain/ConnectionHealthPolicy.kt:5,9-23` |
| watchdog | проверка раз в 5 с, срабатывает при «poll stuck» > 15 с | `TpmsMonitorService.kt:249-266`, `ConnectionHealthPolicy.kt:7` |
| старт цикла | `delay(500)` если не подключено | `TpmsMonitorService.kt:180-183` |
| liveness alarm | 5 мин, heartbeat stale > 90 с | `service/ServiceLivenessScheduler.kt:20-21`, `domain/MonitoringHealthPolicy.kt:8-9` |
| boot retry | 30 / 60 / 120 с (только если `start()` бросил) | `service/BootStartScheduler.kt:17`, `service/BootReceiver.kt:30-41` |
| wake escalation | 0 / 5 / 15 / 30 / 60 / 120 с | `service/WakeEscalationScheduler.kt:16-25` — **мёртвый код**, см. баг B1 |

## Точки входа (кто будит сервис)

| Событие | Обработчик | Что делает | Работает? |
|---|---|---|---|
| `BOOT_COMPLETED` | `BootReceiver.kt:12-43` | `startForegroundService` → полный старт | да (исключение FGS для boot) |
| `DELAYED_BOOT_START` (alarm) | `BootReceiver.kt:14` | повтор старта | на 12+ неточный alarm — не исключение FGS (B9) |
| `SCREEN_ON` | `PowerEventReceiver.kt:19-22` | `WakeEscalationScheduler.onScreenOn` | **нет**: объявлен только в манифесте (`AndroidManifest.xml:102-109`), `registerReceiver` нигде нет; SCREEN_ON/OFF манифест-ресиверам не доставляются (B1) |
| `USB_DEVICE_ATTACHED` | `UsbAttachedReceiver.kt:33-57` | есть разрешение → `wake()` (CHECK_NOW); нет → уведомление + системный диалог | да, но `wake()` не ускоряет переподключение (см. ниже), при неживом сервисе — краш (B3) |
| `USB_DEVICE_DETACHED` | `UsbAttachedReceiver.kt:59-76` | `startService(ACTION_USB_DETACHED)` → `closeUsbForReconnect()` | да, если сервис жив; иначе `IllegalStateException` (поймано, B8) |
| USB permission result | `UsbPermissionReceiver.kt:11-22` | granted → `wake()` | **фактически нет**: PendingIntent `FLAG_IMMUTABLE` (`UsbPermissionHelper.kt:39-44`), система не может дописать `EXTRA_PERMISSION_GRANTED` → всегда `false` (B4) |
| Activity с `USB_DEVICE_ATTACHED` | `MainActivity.kt:70-76`, `AndroidManifest.xml:42-47` | запрос разрешения из UI + `wake()` | только при показанной activity |
| `onCreate/onResume` activity | `MainActivity.kt:35-57` | `start()` + `ensureOnLaunch` → диалог разрешения | нужен пользователь / FrontApp |
| Liveness alarm | `ServiceLivenessReceiver.kt:12-39` | не запущен или heartbeat stale → `start()` | раз в 5 мин; при зависшем цикле — no-op (B10) |
| START_STICKY | `TpmsMonitorService.kt:132` | рестарт после убийства процесса (не после force-stop) | бэкофф системы, от ~1 с до минут |

## Основной цикл (что происходит после старта)

```
startPolling (TpmsMonitorService.kt:177)
 ├─ !connected → Disconnected, delay 500 мс                         :180-183
 └─ loop:
     findDongle()            == null → delay 5 с                     :186-191
     hasUsbPermission()      == false → delay 5 с (запроса нет!)     :193-197
     openDongle()            isSameDevice(VID:PID) → true без open   TpmsRepository.kt:246-249
                             иначе open + handshake/kickstart        TpmsRepository.kt:250-258
                             == false → delay 5 с                    :199-203
     inner while(connected):
        stale > 90 с ? → reconnectStaleFrame, break                  :211-215
        pollOnce() → read (≤3 с) → parse → apply                     :217, :268-285
        isOpenDevicePresent (по пути ИЛИ по VID:PID)                 :219, UsbConnection.kt:50-65
        3× нет → probe(200 мс) → break                               :222-231
        delay 2 с                                                    :239
     closeUsbForReconnect(); delay 5 с                               :242-244
```

## Сценарии

### S1. Холодная загрузка

1. Питание → ядро → system_server; USB-хост перечисляет донгл при `systemReady` (до `BOOT_COMPLETED`). Широковещательный
   `USB_DEVICE_ATTACHED` в этот момент, скорее всего, **не доходит** до ресивера: приложение не directBootAware, а пользователь
   ещё не разблокирован (средняя уверенность).
2. `BOOT_COMPLETED` (на FYT — 20…90 с после питания, очередь упорядоченных broadcast'ов) → `BootReceiver.kt:27` → `startForegroundService`.
3. `onStartCommand` → `promoteToForeground` → `startPolling` (`TpmsMonitorService.kt:117-127`) → 0,5 с → `findDongle`.
4. `hasPermission`:
   - **есть** только если пользователь когда-то поставил «Использовать по умолчанию для этого USB-устройства» (default
     association к `MainActivity` через `device_filter.xml`): тогда система сама выдаёт разрешение при attach и
     **запускает MainActivity поверх экрана**. Иначе разрешение от `requestPermission` живёт в памяти system_server
     и после перезагрузки потеряно.
   - **нет** → цикл крутится по 5 с вечно. Сервис сам разрешение не запрашивает (B7). Диалог появится только при открытии
     activity (`StartupPermissionCoordinator.kt:31-37`) и требует нажатия.
5. `open` → `kickstart` (AA55) / handshake (Deelife) → чтение.

| | Время от `BOOT_COMPLETED` |
|---|---|
| лучший | ~1…2 с + `T_s` (есть association, донгл уже перечислен) |
| неудачный старт FGS | +30 / +60 / +120 с (на 12+ повторы через неточный alarm, скорее всего, тоже упадут, B9) |
| худший | ∞ — нет association → нужен тап по диалогу |

### S2. ACC off → on, процесс жив, донгл перечислен заново с новым путём (`/dev/bus/usb/001/00N+1`)

Во время сна порт обесточен: старое устройство исчезло, при включении появляется новое с тем же VID:PID.

**S2a. `DETACHED` доставлен** (обычно в очереди при засыпании или сразу после пробуждения):
1. `UsbAttachedReceiver.kt:75` → `ACTION_USB_DETACHED` → `closeUsbForReconnect()` параллельно с циклом (`TpmsMonitorService.kt:109-114`).
2. Цикл замечает `!isUsbConnected` после текущей итерации (до 5 с) → ещё раз `closeUsbForReconnect` → **5 с debounce**.
3. Донгл перечисляется 0,5…3 с после подачи питания. Если debounce закончился раньше, `findDongle == null` → ещё **+5 с**.
4. `ATTACHED` → `hasPermission(новое устройство)`:
   - есть (association) → `wake()` → `pollOnce()` **параллельно** циклу на закрытом/старом соединении. Переподключение это не ускоряет, но может дать два одновременных `bulkTransfer` (B13);
   - нет → уведомление + системный диалог → S6.
5. `findDongle` → `openDongle` (соединение `null`, `isSameDevice=false`) → `open` → чтение.

Лучший ~5…7 с + `T_s`; худший ~15…20 с + `T_s`; без association → ∞.

**S2b. `DETACHED` пропущен или обработан до сна** (старое `connection != null`):
1. `read()` на исчезнувшем устройстве сразу возвращает −1. Итерации идут каждые 2 с без кадров.
2. `isOpenDevicePresent` проверяет **по VID:PID** (`UsbConnection.kt:57-61`) → новое устройство считается «нашим» → `absentPolls` не растёт (B6).
3. Спасает только stale frame: если сон длился больше 90 с, на первой итерации `now − lastValidFrameAt > 90 с` → немедленный реконнект → 5 с debounce → `open`. Получается ≈5…10 с + `T_s`. Это случайная «детекция сна» по wall-clock.
4. Если сон был короче 90 с (короткая остановка, перезапуск двигателя) → ждём до 90 с с последнего кадра → худший ≈ 90 + 5 + 5 ≈ 100 с + `T_s`.
5. Если HCD «наполовину» усыплён, `bulkTransfer` зависает в ядре (research §2). Тогда:
   watchdog (15…20 с) → `startPolling` → `openDongle` → `isSameDevice` по VID:PID = true → **то же мёртвое соединение**
   (B5) → второй висящий поток → повтор каждые ~20 с. Через 90 с stale → `close()` (`releaseInterface` может сам
   повиснуть). Liveness видит stale heartbeat, но `start()` — no-op (B10). **Без рестарта процесса или переподключения не восстанавливается.**

### S3. Экран off/on без потери питания USB

1. `SCREEN_OFF/ON` до приложения **не доходят** (B1). Wake escalation не запускается.
2. Если CPU не засыпал, цикл просто продолжает работать: 0 с + `T_s`.
3. Если CPU был в suspend (deep sleep с живым процессом): `delay` стоит на месте (монотонные часы), `bulkTransfer` в полёте
   завершится по таймауту после resume. Если сон > 90 с, stale по wall-clock **ложно** закроет здоровое соединение →
   5 с debounce + reopen ≈ 5…10 с + `T_s`. Если меньше — продолжение без потерь.
4. Если B1 починить «как есть»: `ACTION_PROBE_USB` (`TpmsMonitorService.kt:102-107`) **безусловно** закрывает соединение
   на каждом из 6 событий (0/5/15/30/60/120 с). Это 6 принудительных реконнектов по ≥5 с за 2 минуты, каждый
   сбрасывает `lastValidFrameAt` и ставит `Connecting` (B2).

### S4. Ручное переподключение донгла

1. `DETACHED` → закрытие (как S2a) → ≤5 с + 5 с debounce.
2. Новое перечисление → разрешение потеряно (без association). Система показывает chooser или сразу запускает
   `MainActivity` (association) → `onNewIntent` → `ensureAfterUsbAttach` + `wake()` (`MainActivity.kt:70-76`).
3. `ATTACHED`-ресивер параллельно шлёт второй `requestPermission` + уведомление.

Лучший ~6…12 с + `T_s` (association); без неё — ждёт тапа. После тапа ≤5 с (из-за B4 ресивер не будит, цикл
находит разрешение сам).

### S5. Процесс убит OEM во время сна

- **Обычное убийство процесса** (LMK / kill): при пробуждении возможны три пути старта, кто первый:
  1. START_STICKY → система перезапускает сервис с `intent == null` → полный старт (бэкофф ~1 с…минуты, растёт с числом рестартов).
  2. `ATTACHED` (донгл перечислен заново) → ресивер → `wake()` = `startForegroundService(CHECK_NOW)` → в ветке CHECK_NOW
     нет `startForeground` (`TpmsMonitorService.kt:98-101`) → через ~10 с ANR/краш «did not call startForeground» (B3) →
     ещё один рестарт и рост бэкоффа. Если разрешения нет (а после re-enumeration его нет без association) → только диалог.
  3. Liveness alarm (если не был отменён в `onDestroy`, `TpmsMonitorService.kt:172`) → до 5 мин → `start()`.
  Дальше как S1, п.3–5. Лучший ~2…5 с + `T_s`; типичный — до 5 мин; без разрешения — ∞.
- **force-stop** (многие FYT-«чистильщики» делают именно его): alarms отменены, STICKY не работает, манифест-ресиверы
  не получают broadcast до ручного запуска → **∞**, пока пользователь или FrontApp не откроет приложение.

### S6. USB-разрешение потеряно после re-enumeration (процесс жив)

1. Цикл: `findDongle` ок → `!hasPermission` → `Disconnected`, опрос каждые 5 с (`TpmsMonitorService.kt:193-197`). Сервис сам ничего не запрашивает.
2. Если `ATTACHED` дошёл → уведомление `UsbPermissionNotifier` (на 13+ нужно `POST_NOTIFICATIONS`) + системный диалог
   (`UsbAttachedReceiver.kt:54-55`). На магнитоле диалог может перекрыться навигацией или лаунчером. Если пользователь его закрыл, повторного запроса не будет до следующего attach или открытия activity.
3. Тап «OK» → `UsbPermissionReceiver` получает `granted=false` (B4) → ничего. Цикл видит разрешение ≤5 с → `open`.

Без действия пользователя — **∞**. С тапом — ≤5 с после тапа + `T_s`.

## Где теряются секунды (по убыванию)

1. **Разрешение USB** без default association — бесконечно (S1, S2, S4, S5, S6).
2. **Stale 90 с** как единственный детектор «то же VID:PID, новый путь» при пропущенном DETACHED (S2b, сон < 90 с).
3. **Liveness 5 мин** / бэкофф STICKY / краш из-за B3 при убитом процессе (S5).
4. **Watchdog 15–20 с × N** с переиспользованием мёртвого соединения (S2b, HCD hang) — без выхода.
5. **RECONNECT_DEBOUNCE 5 с** после каждого закрытия, даже когда донгл уже в списке.
6. **USB_CHECK 5 с**, если первый `findDongle` попал до конца перечисления (типично сразу после wake).
7. **Итерация 2…5 с** (read 3 с + delay 2 с) — задержка реакции на `DETACHED`/stale.
8. Внешнее: `T_s` — период передачи датчиков на стоянке; при медленных датчиках возможен лишний stale-реконнект
   каждые ~95 с после открытия (`ConnectionHealthPolicy.kt:17-22` берёт `dongleOpenedAt`).

## Баги и гонки

| # | Где | Суть |
|---|---|---|
| B1 | `AndroidManifest.xml:102-109`, `service/PowerEventReceiver.kt` | `SCREEN_ON/OFF` доставляются только динамически зарегистрированным ресиверам, а `registerReceiver` в коде нет → `WakeEscalationScheduler` и `ACTION_PROBE_USB` на пробуждение никогда не срабатывают. Расходится с AGENTS.md |
| B2 | `TpmsMonitorService.kt:102-107`, `WakeEscalationScheduler.kt:16,22-24,63-64` | Если починить B1: PROBE безусловно рвёт здоровое соединение 6 раз за 2 мин и запускает `pollOnce` параллельно циклу |
| B3 | `TpmsMonitorService.kt:88-115, 411-423` | `wake()/probeUsb()` используют `startForegroundService`, но ветки CHECK_NOW/PROBE_USB/USB_DETACHED выходят до `startForeground`. Если сервис не запущен — ANR/краш через ~10 с. Вызывается из `UsbAttachedReceiver.kt:52`, `UsbPermissionReceiver.kt:18`, `WakeEscalationScheduler.kt:20-21,63-64`, `MainActivity.kt:74` |
| B4 | `data/usb/UsbPermissionHelper.kt:39-44`, `UsbPermissionReceiver.kt:14` | `FLAG_IMMUTABLE` → система не может заполнить extras → `EXTRA_PERMISSION_GRANTED` всегда `false`. Нужен `FLAG_MUTABLE` (intent явный, на 14+ это допустимо) |
| B5 | `data/usb/UsbConnection.kt:43-47`, `TpmsRepository.kt:246-249`, `TpmsMonitorService.kt:257-262` | `isSameDevice` сравнивает только VID:PID → после watchdog/re-enumeration берётся старое мёртвое или висящее соединение |
| B6 | `UsbConnection.kt:57-61`, `TpmsMonitorService.kt:219-231` | `isOpenDevicePresent` с запасным совпадением по VID:PID маскирует re-enumeration → `DETACH_ABSENT_POLLS` никогда не срабатывает |
| B7 | `TpmsMonitorService.kt:193-197` | Сервис не запрашивает разрешение и не показывает уведомление. Если `ATTACHED` пропущен (boot, мёртвый процесс), пользователь не узнает о причине |
| B8 | `UsbAttachedReceiver.kt:72-75`, `TpmsMonitorService.kt:109-114` | DETACHED: `startService` из фона падает, если сервис не FGS (поймано). `closeUsbForReconnect` идёт параллельно с `read` в цикле, а `close()` не будит висящий `bulkTransfer` (research §1) |
| B9 | `BootStartScheduler.kt:35`, `ServiceLivenessScheduler.kt:24`, `WakeEscalationScheduler.kt:42` | `setAndAllowWhileIdle` — неточный alarm. На 12+ он не даёт права на старт FGS из фона (нужны `setExact*`/alarm clock, исключение по батарее или SAW) → `start()`/`wake()` из alarm может бросить `ForegroundServiceStartNotAllowedException` (в liveness поглощается `runCatching`). На Teyes (Android 10) не актуально |
| B10 | `ServiceLivenessReceiver.kt:22-33`, `TpmsMonitorService.kt:125-130` | Stale heartbeat → `start()` → `pollingJob.isActive` = true (висит) → ничего не происходит. Нет пути «перезапустить процесс» |
| B11 | `TpmsMonitorService.kt:242-244` | Debounce 5 с безусловный, в том числе для stale/forced-реконнекта при донгле, который уже в списке |
| B12 | `ConnectionHealthPolicy.kt:9-23` | Stale по wall-clock: после сна > 90 с всегда рвёт соединение, даже здоровое (S3). Полезно как случайный wake-детектор, но это не намеренное поведение |
| B13 | `TpmsMonitorService.kt:98-100` | CHECK_NOW запускает `pollOnce` параллельно циклу → два `bulkTransfer` на один endpoint (пересекается с тикетом 06) |
| B14 | `TpmsMonitorService.kt:172` | `onDestroy` отменяет liveness alarm → после уничтожения сервиса системой страховкой остаётся только STICKY |
| B15 | `AndroidManifest.xml:42-47` + default association | Единственный способ пережить re-enumeration без тапа (association) заодно **выводит MainActivity поверх экрана** на каждом attach. Онбординг не просит ставить галочку «по умолчанию» |
