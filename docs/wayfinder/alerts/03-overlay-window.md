---
title: Как показать окно тревоги поверх всего на Teyes и Android 9–15
labels: [wayfinder:research]
status: closed
assignee: drhaos+claude
blocked_by: []
---

## Question

Красное (и жёлтое) окно должно появляться поверх любого приложения — навигации, музыки, FrontApp — из foreground-сервиса. Что для этого работает и где ломается?

- `TYPE_APPLICATION_OVERLAY` из сервиса с `SYSTEM_ALERT_WINDOW`: поведение на Android 9–15, ограничения Android 12+ (непрозрачные окна и касания, `FLAG_NOT_TOUCH_MODAL`, untrusted touches), Android 15 (оверлей и FGS).
- Как выдаётся разрешение на FYT/Teyes (есть ли экран настроек, сбрасывается ли), поведение поверх камеры заднего хода, при сне/пробуждении.
- Запасной путь без разрешения: full-screen intent (при включённом экране = только heads-up?), запуск activity из фона (BAL-исключения).
- Кнопка «Понятно» в оверлее: фокус, касания, доступность при движении.
- Как уже устроен `FloatingOverlayController` в приложении и что из него переиспользуется.

Находки: ветка `research/overlay-window`, файл `docs/research/overlay-window.md`.

## Resolution

Подробно: ветка `research/overlay-window`, файл `docs/research/overlay-window.md` (2026-11-17).

- `TYPE_APPLICATION_OVERLAY` из сервиса с `SYSTEM_ALERT_WINDOW` работает одинаково на Android 9–15: слой выше любой activity (навигация, музыка, FrontApp), ниже шторки и системных окон.
- Блокировка касаний Android 12+ кнопке не мешает (касается только сквозных касаний); окно с `FLAG_NOT_FOCUSABLE` — кнопка нажимается, касания мимо уходят навигации; кнопки руля / Back окно не получает.
- Android 15 показ не меняет; при установке не из магазина «Поверх других приложений» — ограниченная настройка (сначала «⋮ → Разрешить ограниченные настройки»); на 11+ `package:`-ссылка открывает только общий список.
- Без разрешения поверх чужого приложения окна нет: при включённом разблокированном экране full-screen intent = только heads-up (липкий при разрешённом FSI, иначе ~60 с), фоновый запуск activity на 10+ запрещён; на Android 9 `startActivity` из сервиса работает.
- Риски: камера заднего хода FYT (если это activity — окно её закроет), процесс убит во сне — окно поднимать заново из сохранённого состояния. Данных по FYT/Teyes мало; запас — `adb shell appops set <pkg> SYSTEM_ALERT_WINDOW allow`.

**Решение (рекомендация исследования):** отдельный `AlertOverlayController` (`TYPE_APPLICATION_OVERLAY`, `FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN`, не во весь экран, кнопка нажимается) заменяет запуск activity в `takeOverScreen()`; «Поверх других окон» — обязательный шаг онбординга с подсказками для 11+/15/Teyes; без разрешения — несмахиваемое уведомление + FSI + звук (+ `startActivity` на Android 9); состояние «показано / подтверждено» хранится вне процесса; общий помощник add/update/remove для обоих оверлей-контроллеров. Попутно чинить в `FloatingOverlayController`: `addView` без `runCatching`, «показан» после отзыва разрешения, `createWindowContext`, мёртвая ветка `TYPE_PHONE`.
