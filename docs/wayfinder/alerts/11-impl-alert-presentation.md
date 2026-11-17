---
title: Реализовать чистую политику показа тревог AlertPresentation
labels: [wayfinder:task]
status: closed
assignee: drhaos+claude
blocked_by: [10]
---

## Question

Код + unit-тесты по [05](05-alert-lifecycle.md) и [08](08-alert-module-boundaries.md).

## Resolution

Сделано 2026-11-17 (код не собран: SDK в VM нет; сборка и тесты — на Windows). `domain/AlertPresentation.kt` (step / acknowledge / muteAttention / dismissBanner / encode / decode) + `AlertPresentationTest`; `AlertNotificationPolicy` / `AlertNotificationBook` и их тесты удалены.
