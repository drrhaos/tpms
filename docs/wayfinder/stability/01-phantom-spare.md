---
title: Откуда фантомная запаска SP в v1.1.3
labels: [wayfinder:task]
status: closed
assignee: agent-diagnost
blocked_by: []
---

## Question

На v1.1.3 при **выключенном** «Показывать запаску» и без датчика в запаске иногда появляется колесо SP. По коду SP отсекается в `SensorValidator`, `TpmsRepository`, `WheelLayout`, `WidgetSnapshot` — значит, есть неотфильтрованный путь.

Найти его: проследить все места, где формируются метки колёс (главный экран `CarTopDown`, виджеты, постоянное уведомление, плавающее окно, экран настроек/маппинга, восстановление снапшота, гонка загрузки `SettingsStore`), и всё, что может выдать сенсор с id/label `SP` или занять 5-й слот (в т.ч. `SENSOR_XX` из мусорного кадра AA55 через fallback `elementAtOrNull` в `WheelLayout.orderedSlots`). AFK.

Результат: точная причина (или ранжированный список кандидатов) + описание воспроизводящего unit-теста.

## Подсказки

- Из [Что означают байты статуса и коды шин в протоколе 55AA](03-aa55-status-flags.md): слот `05` **без датчика шлёт фиктивное значение** (`46 46` / `49 53`) бесконечно — проверить, при каком пути это значение доходит до UI при выключенной запаске.

## Resolution

**Вывод:** на v1.1.3 (HEAD) не найдено ни одного пути, по которому плитка с меткой `SP` попадает в UI, если в `SettingsStore` записано `showSpareWheel=false`. Метку `SP` даёт только `WheelLayout.allSlots(true)` (`WheelLayout.kt:10-11`). Все поверхности строятся от `allSlots(showSpareWheel)`: `MainViewModel.kt:98`, `WidgetSnapshot.kt:51,87`, `DiagnosticsExporter.kt:44`, `SettingsViewModel.kt:308`. Уведомление и оверлей рисуют только 4 слота (`WidgetRemoteViews.kt:62-74`, `FloatingOverlayController.kt:71`). Строка `widget_sp_row` по умолчанию `gone` (`widget_tpms_car.xml:250`) и включается только при метке SP (`WidgetRemoteViews.kt:162-163`). Данные SP режутся на четырёх уровнях: `SensorValidator.kt:20`, `TpmsRepository.kt:381,99,450`, `WheelLayout.kt:20-25`. Гонка загрузки `SettingsStore` безвредна: значение по умолчанию `false` (`SettingsStore.kt:69,129`). Кадры `09` отбрасываются (`Aa55SerialProtocol.kt:123`).

**Кандидаты (по убыванию вероятности):**
1. **«Фантомное колесо» — это не SP, а мусорный `SENSOR_XX` или дубль, попавший в слот через fallback.** Уверенность: средняя-высокая, что это реальный дефект. Кадр AA55 с верным XOR и неизвестным POS превращается в `SENSOR_%02X` (`Aa55SerialProtocol.kt:166`). Ни валидатор, ни репозиторий его не режут. Он живёт в `_sensors` до `closeUsb`, сохраняется в LastKnown-снапшот, а через `sensorTimeoutMs` получает `SENSOR_LOST` (`TpmsRepository.kt:303-319`). Тогда всё приложение уходит в `Alert` (`:407-416`), а в шапке появляется «SENSOR_07: потерян» (`MainStatusUi.kt:32`). Если нет своего маппинга, `orderedSlots` заполняет пустой слот через `usableSensors.values.elementAtOrNull(index)` (`WheelLayout.kt:32`). Отсюда мусор в RR, пока нет кадра RR, и дубль FR в слоте FL в первые секунды после реконнекта: карта к этому моменту очищена, а порядок вставки случайный. HID-донгл вообще не умеет отличать запаску (`HidGenericProtocol.kt:20-24`, метка пустая), поэтому его 5-й датчик тоже уходит в fallback по индексу.
2. **Флаг на самом деле `true`.** Уверенность: средняя для настоящей метки «SP». Переключатель есть только в расширенном режиме (`SettingsScreen.kt:346`, `if (advanced)`). `allowBackup="true"` (`AndroidManifest.xml:26`) восстанавливает `tpms_settings` после переустановки. Импорт JSON (`SettingsStore.kt:277`) тоже может включить запаску. Пользователь в режиме USER не видит, что она включена.
3. **Гонка при выключении запаски.** Уверенность: низкая. `applySensorUpdate` делает неатомарный read-modify-write (`TpmsRepository.kt:385-387`) параллельно с `removeSpareSensors` (`:426-440`) и может вернуть SP в карту. В UI SP не попадёт, но окажется в `knownSensorIds` (`SettingsViewModel.kt:137-139`), то есть в выборе маппинга, и в diagnostics как `unmapped SP`.

**Решение (не реализовано):**
- Парсер AA55 отбрасывает неизвестный POS (`else -> null`) и пишет сырые байты в `TPMS_RAW`. Передать в тикет 04.
- В `WheelLayout.orderedSlots` убрать fallback по индексу для сенсоров с непустой меткой. Оставить его только для HID (пустая метка) и без повторов: один сенсор не может занять два слота.
- Правило допуска в репозиторий: в `_sensors` попадают только сенсоры, у которых метка входит в `allSlots(showSpare)`, или id есть в `wheelMapping`, или метка пустая (HID). Все записи делать через `_sensors.update {}`, чтобы закрыть гонку из кандидата 3.
- В `exportFullReport` выводить `show_spare_wheel`, `settings_ui_mode` и id всех сенсоров в карте. Так кандидат 2 подтверждается одним отчётом.

**Воспроизводящие тесты** (сейчас красные):
- `WheelLayoutTest.orderedSlots_doesNotFillMissingWheelWithUnknownSensor`: карта `linkedMapOf(FL, FR, RL, "SENSOR_07"→label "SENSOR_07")`, маппинг пустой, `showSpare=false`. Проверка `slots[3] == null`. Сейчас там `SENSOR_07`.
- `WheelLayoutTest.orderedSlots_doesNotDuplicateSensorAcrossSlots`: карта `linkedMapOf(FR, RL, RR)` с метками. Проверка `slots[0] == null` и что все id в слотах различны. Сейчас FR стоит в двух слотах.
- `Aa55SerialProtocolTest.unknownTireCode_isDropped`: `feed(buildTireStateFrame(0x07, 0x46, 0x46, 0))`. Ожидается пустой список. Сейчас возвращается `SENSOR_07`.
