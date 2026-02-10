# F3. Jira Sync & Cache

## Обзор

Инкрементальная синхронизация задач из Jira с локальным кэшированием, отслеживанием статусов и защитой локальных данных от перезаписи.

## Алгоритм синхронизации

### Полная (первый запуск)
```
JQL: project = {KEY} ORDER BY updated DESC
```

### Инкрементальная (последующие)
```
JQL: project = {KEY} AND updated >= '{lastSync - 1min}' ORDER BY updated DESC
```
Вычитание 1 минуты — защита от пропуска задач на границе времени.

### Расписание
- `@Scheduled` с интервалом `jira.sync-interval-seconds` (по умолчанию 300с)
- Ручной запуск: `POST /api/sync/trigger` (только Admin)

## Upsert-логика

При обновлении задачи:

1. **Поиск** по `issue_key` (unique constraint)
2. **Сохранение локальных полей** во временные переменные:
   - `roughEstimate*` (SA/DEV/QA дни) + метаданные
   - `autoScore` + `autoScoreCalculatedAt`
   - `doneAt`, `manualOrder`
3. **Перезапись** всех полей из Jira (summary, status, estimates, assignee и т.д.)
4. **Восстановление** сохранённых локальных полей
5. **Сохранение** в БД

**Принцип:** Jira-данные обновляются, локальные overlay-данные сохраняются.

## Status Changelog

При обнаружении смены статуса:

1. Сравнение `previousStatus` с `newStatus`
2. Запись `StatusChangelogEntity`:
   - `issueKey`, `fromStatus`, `toStatus`
   - `transitionedAt`, `timeInPreviousStatusSeconds`
3. Дедупликация по уникальному индексу `(issue_key, to_status, transitioned_at)`
4. Автоматическое обновление `doneAt` при переходе в Done-статус

## Управление состоянием синхронизации

`JiraSyncStateEntity` — одна запись на проект:

| Поле | Назначение |
|------|-----------|
| `lastSyncStartedAt` | Время начала синхронизации |
| `lastSyncCompletedAt` | Время завершения (используется для JQL) |
| `lastSyncIssuesCount` | Количество обработанных задач |
| `syncInProgress` | Флаг блокировки |
| `lastError` | Сообщение об ошибке |

## Защита от параллелизма

- Проверка `syncInProgress` флага перед началом
- Флаг сохраняется в БД сразу после установки
- При ошибке — флаг сбрасывается, ошибка записывается

## Кэшируемые поля

**Из Jira:** issueKey, summary, status, issueType, parentKey, estimates, teamFieldValue, priority, dueDate, assignee, flagged, blocks/isBlockedBy

**Локальные (не перезаписываются):** roughEstimate*, autoScore, startedAt, doneAt, manualOrder

## Схема БД

- `jira_issues` (V1) — кэш задач, 30+ колонок
- `jira_sync_state` (V1) — состояние синхронизации
- `status_changelog` (V17) — история переходов статусов

## Интеграции после синхронизации

- Пересчёт AutoScore для эпиков и stories
- Маппинг статусов через StatusMappingService
- Извлечение team field и маппинг на локальные команды
