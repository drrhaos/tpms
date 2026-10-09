# Сигналы FYT/Teyes при ACC on/off и выходе из сна

Исследование к тикету [12-teyes-acc-broadcasts](../wayfinder/stability/12-teyes-acc-broadcasts.md).
Контекст: [wake-timeline](../wayfinder/stability/assets/wake-timeline.md) (баг B1: `SCREEN_ON` объявлен только в манифесте).

Ограничения: железа нет, XDA (xdaforums.com) отдаёт 403 для прямого чтения, поэтому по XDA использованы
поисковые выдержки. Первичными считаем исходники на GitHub (манифесты и код приложений, которые реально
работают на FYT), вторичными — форумы.

## Сводная таблица

| Сигнал | Когда | Доставка (manifest / runtime) | Источник | Уверенность |
|---|---|---|---|---|
| `com.fyt.boot.ACCON` | ACC on, в том числе выход из глубокого сна (шлёт `com.syu.ms`) | Неявный broadcast. На Android 8+ **в манифестный ресивер стороннего приложения не приходит**: «Acc On does certainly not work on Android 8.x and higher… if not a system signed app». Динамическая регистрация из живого процесса теоретически работает, но подтверждения для UIS7862 нет | [FytHWOneKey README](https://github.com/hvdwolf/FytHWOneKey), [FytFunctionalityExtender](https://github.com/hvdwolf/FytFunctionalityExtender) (`AndroidManifest.xml`, `AccOnReceiver.java`), [HeadUnit Revived gist](https://gist.github.com/andrecuellar/3c39b4f1bdc03a5c4e97336217f13c38) | высокая (manifest не работает); низкая (runtime работает) |
| `com.fyt.boot.ACCOFF` | ACC off, перед уходом в сон (~25 с) | То же, что ACCON | те же | высокая / низкая |
| `com.glsx.boot.ACCON` / `ACCOFF` | Варианты прошивок с GLSX | То же | [FytFunctionalityExtender manifest](https://github.com/hvdwolf/FytFunctionalityExtender/blob/master/app/src/main/AndroidManifest.xml), [headunit-revived manifest](https://github.com/noambergauz/headunit-revived/blob/main/app/src/main/AndroidManifest.xml) | средняя |
| `android.intent.action.ACTION_MT_COMMAND_SLEEP_OUT` / `_SLEEP_IN` | Выход из сна / уход в сон (старые FYT/MTK) | Неявный, ограничения как выше | FytFunctionalityExtender manifest, headunit-revived manifest | низкая для UIS7862 |
| `com.microntek.startApp`, `com.microntek.bootcheck` | **MTCD/Microntek** (не FYT): пробуждение / уход в сон | Неявный | [gist](https://gist.github.com/andrecuellar/3c39b4f1bdc03a5c4e97336217f13c38), XDA [tasker after sleep](https://xdaforums.com/t/is-there-a-way-to-start-a-app-after-unit-wakes-up-from-sleep-mode-with-tasker.3412181/) | средняя (к Teyes не относится) |
| `com.cayboy.action.ACC_ON`, `com.carboy.action.ACC_ON`, `xy.android.acc.on`, `autochips.intent.action.QB_POWERON` | Другие платформы (PX3, XYAuto, Autochips) | Неявный | headunit-revived manifest | средняя (не FYT) |
| `android.intent.action.ACC_ON` | — | Упоминаний для FYT не найдено | — | — |
| `BOOT_COMPLETED` | **Только холодный старт**: сон выключен в заводских настройках, после ~10 снов (плановая перезагрузка), после 3 суток стоянки, при сбое сна | Манифест — да (исключение для фонового старта FGS) | [teyes-auto FAQ / поиск по «Отключение ГУ → Сон»](https://www.teyes-auto.ru/chasto-zadavaemye-voprosy-po-avtomagnitolam-teyes/), [drive2: «через ~10 снов перезагрузка»](https://www.drive2.ru/l/644185446967960074/), XDA [Deep sleep issue UIS7862](https://xdaforums.com/t/deep-sleep-issue-uis7862-mekede-with-joying-firmware.4647313/) | высокая |
| `QUICKBOOT_POWERON` (и HTC/MTK-варианты) | На FYT после сна **не шлётся** (FYT использует suspend-to-RAM, а не IPO/QuickBoot). Нужен только для MTK/Autochips | Манифест | headunit-revived manifest (перечисляет «на всякий случай»); прямых подтверждений для FYT нет | средняя |
| `SCREEN_ON` / `SCREEN_OFF` | При каждом выходе из сна / уходе в сон, на всех платформах | **Только runtime** (`registerReceiver` из живого процесса). Манифест не получает никогда | AOSP-ограничение; [headunit-revived `AapService.kt`](https://github.com/noambergauz/headunit-revived/blob/main/app/src/main/java/com/andrerinas/openheadunit/aap/AapService.kt) («ACTION_SCREEN_ON can only be received by dynamically registered receivers… fires on ALL devices after hibernate») | высокая |
| `USB_DEVICE_ATTACHED` | После пробуждения, если порт обесточивался и донгл переэнумерирован | Манифест / activity. **Может не прийти** после hibernate: headunit-revived поэтому при wake/boot сам вызывает `checkAlreadyConnected(force = true)` | headunit-revived `BootCompleteReceiver.kt` («On hibernating head units, USB_DEVICE_ATTACHED may not fire after wake») | средняя |
| Автозапуск FYT после сна (`com.syu.ms` стартует приложения из списка) | Холодный старт и выход из глубокого сна | Не broadcast: системный `ms.apk` запускает приложения. На UIS7862: Настройки → «Пользователь» → «Настройки приложений» → автозапуск. FYTStarter / FytHWOneKey подменяют пакеты/активити, которые `ms.apk` стартует сам (activity-alias `com.syu.*`) | XDA [UIS7862 Q&A thread](https://xdaforums.com/t/general-fyt-based-spreadtrum-uis7862-s-unisoc-ums512-uis8581a-sc9863-q-a-mods-tips-firmware.4396339/page-19), [FytHWOneKey manifest](https://github.com/hvdwolf/FytHWOneKey/blob/master/app/src/main/AndroidManifest.xml) | средняя |
| FrontApp (`ru.fytmods.frontapp`) | Ставит выбранное приложение на «главный экран» Teyes, лаунчер поднимает его после сна | Запуск activity | [Google Play / AppBrain](https://www.appbrain.com/app/frontapp-for-teyes/ru.fytmods.frontapp) | средняя |

## Поведение сна, процессов и USB

1. **Режимы.** ACC off → таймер ~25 с → suspend-to-RAM (ток 4–6 мА). Через 3 суток без запуска, примерно после
   каждых ~10 снов, а также если сон отключён в «Завод → Отключение ГУ → Сон» (пароль 168), магнитола делает
   полное выключение, и при следующем ACC on идёт холодный старт (до 2 мин). Поэтому реально встречаются оба
   пути: «wake из сна» и «холодный boot». Уверенность средняя-высокая.
2. **Процессы.** `com.syu.ms` при уходе в сон вызывает `ToolkitApp.killAppWhenSleep()` → `KillAllAppButSome()`:
   убивает сторонние приложения, которых нет в белом списке (виден hook в [XFytTweaker](https://github.com/hvdwolf/XFytTweaker/blob/master/app/src/main/java/org/hvdw/xfyttweaker/XFytTweaker.java)).
   Белые списки: `skipkillapp.prop` (на UIS7862 он стал OOM-приоритетами), `/oem/app/pwctl_config.xml`
   (удалён в прошивках с ядром от 2023-12 и новее). На Teyes закрытие приложений во сне — известная проблема:
   помогает только патч/мод NoKill ([4pda Teyes UIS7862](https://4pda.to/forum/index.php?showtopic=1004230&st=4820),
   [drive2](https://www.drive2.ru/l/644185446967960074/)). Вывод: **на стоковой прошивке процесс приложения
   после сна часто мёртв, причём это force-stop, а не LMK** (alarms и STICKY не помогают). Уверенность средняя.
   Это совпадает со сценарием S5 «force-stop» в wake-timeline.
3. **USB.** Данные противоречат друг другу. Есть сообщения, что на FYT во сне USB остаётся под 5 В
   (видеорегистраторы и LED продолжают работать: XDA [What exactly is FYT](https://xdaforums.com/t/what-exactly-is-fyt-a-deep-dive.4619775/)).
   Есть и такие, где после пробуждения USB-порты «как отключены» и оживают только после смены режима USB/перезагрузки
   ([XDA Head unit doesn't detect USB](https://xdaforums.com/t/head-unit-doesnt-detect-usb.4219953/),
   [XDA FYT USB mode](https://xdaforums.com/t/how-to-change-fyt-head-units-sc9853i-uis7862-cc3-usb-mode.4218377/)).
   TPMS-приложения после сна теряют донгл, лечится force-close и перезапуском
   ([XDA TPMS problem 8.1](https://xdaforums.com/t/tpms-problem-on-android-8-1-head-unit.3889231/),
   [4pda TPMS](https://4pda.to/forum/index.php?showtopic=1004199&st=880)). Разрешение USB «по умолчанию» не всегда
   переживает перезагрузку. Практический вывод: код должен работать в обоих случаях — (а) тот же `deviceName`, но
   «полумёртвый» HCD; (б) переэнумерация с новым `/dev/bus/usb/...` без `ATTACHED`. Уверенность низкая-средняя.
4. **Штатное приложение Teyes TPMS** после сна разворачивается на весь экран (его стартует `ms.apk`), то есть
   OEM-механизм автозапуска после сна существует и работает для системных приложений.

## Как это делают другие

- **headunit-revived / open-headunit** (Android Auto, поддерживает FYT): в манифесте стоит «широкая сеть»
  (`BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, `QUICKBOOT_POWERON`, `com.fyt.boot.ACCON`, `com.glsx.boot.ACCON`,
  `ACTION_MT_COMMAND_SLEEP_OUT`, MTCD/XY/Autochips). Основной детектор — **runtime `SCREEN_ON` в foreground-сервисе**
  с порогом «экран был выключен > 60 с» = hibernate wake → принудительная проверка уже подключённых USB-устройств.
  Все сработавшие действия пишутся в лог с префиксом `WakeDetect:`, чтобы собирать данные с разных устройств.
- **FytFunctionalityExtender / FytHWOneKey**: манифестные `ACCON/ACCOFF` работают только на Android ≤ 7. Автор
  прямо признаёт, что на 8+ это не работает.
- **FYTStarter**: выдаёт себя за пакет, который `ms.apk` сам запускает после сна, потому что «нет интента, по
  которому можно понять, что юнит проснулся».
- **Tasker на ГУ (Android 9)**: после suspend-to-RAM профили перестают срабатывать, cold boot работает
  ([Tasker group](https://groups.google.com/g/tasker/c/UxaZAjg81Qw)). Типовое решение сводится к событию «Display on».
- Torque, MTPMSU: публичных данных о том, как они ловят wake на FYT, не найдено.

## Рекомендация по триггеру пробуждения

1. **Основной: runtime `SCREEN_ON`/`SCREEN_OFF`**, регистрировать в `TpmsMonitorService.onCreate`, снимать в
   `onDestroy`. На `SCREEN_OFF` запоминать `elapsedRealtime`. На `SCREEN_ON`: если экран был выключен > ~30–60 с,
   считаем это wake и делаем **одну** мягкую проверку USB: пересканировать `deviceList`, сравнить `deviceName`
   (не только VID:PID), переоткрыть соединение только если путь изменился или чтение мёртвое. Не нужны 6 безусловных
   PROBE (B2). Манифестный `PowerEventReceiver` убрать или оставить как no-op.
2. **Дополнительно в манифест `BootReceiver`** (дёшево, ничего не ломает): `com.fyt.boot.ACCON`,
   `com.glsx.boot.ACCON`, `android.intent.action.ACTION_MT_COMMAND_SLEEP_OUT`, `QUICKBOOT_POWERON`. На Teyes
   Android 10 они, скорее всего, не придут, но логировать их нужно, чтобы проверить на железе. Те же action
   продублировать в runtime-ресивере сервиса.
3. **На случай мёртвого процесса** (force-stop при сне) broadcast-путей нет. Остаётся OEM-автозапуск: добавить
   приложение в автозапуск после сна в настройках FYT («Настройки приложений») или через FrontApp (уже есть
   embedded-режим). Онбординг Teyes должен явно об этом просить. `MainActivity`/FrontApp на старте вызывает
   `start()` + проверку USB.
4. Сохранить «страховку» по wall-clock (stale > 90 с), но сделать её осознанной: скачок `currentTimeMillis` против
   `elapsedRealtime`/`uptimeMillis` (uptime во сне не растёт) → это детектор suspend, не зависящий от broadcast'ов.

## Открытые вопросы (нужна проверка на железе)

1. Приходит ли `com.fyt.boot.ACCON` в **динамический** ресивер на CC3/CC3 2K (Android 10/13)? Это защищённый
   broadcast или с permission? Проверка: `logcat` + `dumpsys activity broadcasts | grep -i acc` после ACC on.
2. Убивает ли стоковый Teyes наш foreground-сервис при сне (force-stop или нет)? Проверка:
   `dumpsys package com.tpms.app | grep stopped` после пробуждения.
3. Обесточивается ли USB-порт во сне на CC3/CC3 2K, меняется ли `deviceName`, приходят ли `DETACHED/ATTACHED`?
   Проверка: `dumpsys usb` до и после сна, логи `TPMS_HID`.
4. Где на Teyes находится меню «автозапуск после сна» и работает ли оно для сторонних приложений на свежих
   прошивках (pwctl_config.xml удалён)?
5. Насколько часто в реальности бывает холодный старт (каждые ~10 снов, 3 суток)? Значит ли это, что
   `BOOT_COMPLETED` + USB-разрешение (B7/B15) важнее, чем wake?
