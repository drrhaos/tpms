---
title: Реализовать уровни тревог: AlertLevel, PRESSURE_DROP, скорость падения
labels: [wayfinder:task]
status: closed
assignee: drhaos+claude
blocked_by: []
---

## Question

Код + unit-тесты по [01](01-severity-classification.md) и [08](08-alert-module-boundaries.md): `AlertLevel`, `TireSensor.alertLevel`, `PRESSURE_DROP`, пороги 65 % / +15 °C / 30 кПа за 10 мин, перекос оси и потеря датчика в `WheelCrossCheck`, `AlertSeverity` из уровня, строки.

## Resolution

Сделано 2026-11-17 (код не собран: SDK в VM нет; сборка и тесты — на Windows). `AlertLevel` + `TireSensor.alertLevel` / `recentDropKpa`, `PRESSURE_DROP`; `AlertEvaluator` переписан (уровни, критические пороги в `AlertThresholds`, скорость падения на образцах в `AlertMemory`, `AlertRank`); `WheelCrossCheck` даёт FAULT (`PRESSURE_DROP` по перекосу); `AlertSeverity` — цвет из уровня; снимок хранит уровень; подписи/советы `PRESSURE_DROP`; тесты `AlertEvaluatorTest` (переписан), `AlertSeverityTest`, `WheelCrossCheckTest`.
