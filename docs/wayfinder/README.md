# Wayfinder — локальный markdown-трекер

Карты и тикеты wayfinder хранятся в репозитории, а не во внешнем трекере.

## Wayfinding operations

| Операция | Как выражается |
|---|---|
| Карта | `docs/wayfinder/<effort>/map.md`, во frontmatter `labels: [wayfinder:map]` |
| Тикет (дочерний issue карты) | `docs/wayfinder/<effort>/NN-<slug>.md` в папке карты; id = `NN` |
| Тип тикета | frontmatter `labels: [wayfinder:<research\|prototype\|grilling\|task>]` |
| Статус | frontmatter `status: open \| closed` |
| Claim | frontmatter `assignee: <имя>`; пусто = не захвачен |
| Блокировка | frontmatter `blocked_by: [NN, ...]` — нативной блокировки нет, это конвенция в теле |
| Резолюция | секция `## Resolution` в конце тикета + `status: closed` |
| Активы | ссылки из тикета; research-находки — на ветке `research/<slug>` |

**Frontier** карты — тикеты с `status: open`, пустым `assignee` и всеми `blocked_by` в статусе `closed`.

Быстрый просмотр статусов:

```bash
grep -H -E '^(title|status|assignee|blocked_by):' docs/wayfinder/<effort>/[0-9]*.md
```
