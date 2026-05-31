# Teyes CC3 Emulator Profile

## Способ 1 — Device Manager (Android Studio)

1. File → Settings → Appearance → **Device Definitions** → кнопка `+` → Import
2. Выбрать `tools/device_profile.xml`
3. Затем Tools → **AVD Manager** → + → выбрать "Teyes CC3" → образ API 29

Или вручную: скопировать содержимое `device_profile.xml` в `~/.android/devices.xml` внутрь корневого тега `<d:devices>`.

## Способ 2 — скрипт

```bash
bash tools/create_avd.sh
```

Требует: Java, Android SDK tools (`avdmanager`), образ `system-images;android-29;default;x86`

## Способ 3 — config.ini напрямую

```bash
avdmanager create avd \
  -n Teyes_CC3 \
  -k "system-images;android-29;default;x86" \
  -d "7.0\" WSVGA Tablet" \
  -f

# Затем перезаписать config.ini нашим
cp tools/config.ini.teyes_cc3 ~/.android/avd/Teyes_CC3.avd/config.ini
```

## Характеристики Teyes CC3

| Параметр | Значение |
|----------|----------|
| SoC      | Rockchip PX6 (4×Cortex-A72) |
| RAM      | 2-4 GB |
| Экран    | 7" IPS 1280×720 |
| Android  | 8-10 (API 28-29) |
| USB      | 2×USB Host + 1×USB OTG |
| Звук     | CAN Bus → внешний усилитель |
| Навигация| Софт-кнопки (нет hw keys) |
