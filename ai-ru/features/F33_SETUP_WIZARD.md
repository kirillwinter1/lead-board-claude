# F33: Setup Wizard + Refresh Teams Sync

## Статус: ✅ Done (2026-02-15)

## Проблема

При первом заходе администратор видит пустую доску и не понимает, что делать. Нет guided flow для первоначальной настройки. Также синхронизация команд (`TeamSyncService`) вызывалась отдельно и не была привязана к основному sync flow.

## Решение

### 4-Step Setup Wizard

При первом входе (если `lastSyncCompletedAt == null`) вместо доски показывается wizard:

1. **Period** — выбор периода синхронизации (N месяцев), проверка количества задач в Jira через `GET /api/sync/issue-count?months=N`
2. **Sync** — запуск синхронизации с прогресс-баром и счётчиком импортированных задач в реальном времени
3. **Workflow** — встроенная конфигурация workflow (WorkflowConfigPage рендерится inline, с кнопкой Skip)
4. **Done** — переход на доску

### Refresh Teams Sync

Team sync автоматически вызывается в конце каждого `syncProject()`. Кнопка "Sync from Atlassian" на вкладке Teams убрана — teams синкаются вместе с задачами.

### Scheduler Guard

Scheduled sync (`@Scheduled`) не запускается, пока `lastSyncCompletedAt == null` — чтобы не заполнить данные до прохождения wizard.

## Файлы

### Новые
| Файл | Описание |
|------|----------|
| `frontend/src/pages/SetupWizardPage.tsx` | 4-step wizard с stepper, localStorage persistence |
| `frontend/src/pages/SetupWizardPage.css` | Стили wizard (stepper, cards, progress bar, spinner) |

### Изменённые
| Файл | Изменение |
|------|-----------|
| `sync/SyncController.java` | +`GET /issue-count?months=N`, `POST /trigger?months=N` |
| `sync/SyncService.java` | +`countIssuesInJira()`, months→days JQL, +teamSyncService, scheduler guard |
| `jira/JiraClient.java` | +`countByJql()` — подсчёт через cursor pagination |
| `jira/JiraSearchResponse.java` | +`@JsonProperty("isLast")` — fix десериализации |
| `sync/SyncServiceTest.java` | +TeamSyncService mock, тесты countIssuesInJira, months filter |
| `frontend/src/components/Layout.tsx` | +sync status check → wizard/outlet, скрытие навигации |
| `frontend/src/pages/TeamsPage.tsx` | Убрана кнопка "Sync from Atlassian" |
| `frontend/src/pages/WorkflowConfigPage.tsx` | +`onComplete` prop для inline wizard use |

## API

### Подсчёт задач в Jira
```
GET /api/sync/issue-count?months=6
→ { "total": 244, "months": 6 }
```

### Запуск синка с фильтром по периоду
```
POST /api/sync/trigger?months=6
→ { "syncInProgress": true, ... }
```

Months конвертируются в дни (`months * 30`) в JQL, т.к. Jira `m` = минуты, а не месяцы.

## Ключевые решения

### JQL: months → days
Jira JQL использует `m` для минут, `h` для часов, `d` для дней, `w` для недель. Нет единицы для месяцев. Формула: `updated >= -{months*30}d`.

### isLast десериализация
Jackson маппит boolean getter `isLast()` на JSON property `last` (strip "is" prefix). Jira возвращает `isLast`. Fix: `@JsonProperty("isLast")`.

### localStorage + DB validation
Wizard step сохраняется в localStorage для persistence при навигации. При mount проверяется `issuesCount` через API — если 0 (БД пересоздана), сбрасывается на step 1.

### Inline WorkflowConfigPage
Step 3 рендерит WorkflowConfigPage inline (не навигация на отдельную страницу). `onComplete` prop вызывается после сохранения или skip.

## Тесты

- `SyncServiceTest.countIssuesInJira()` — подсчёт с months filter
- `SyncServiceTest.countIssuesInJira()` — подсчёт без filter
- `SyncServiceTest.syncProject()` — months filter для first sync
- `SyncServiceTest.syncProject()` — incremental sync игнорирует months

## Связанные фичи

- [F3: Jira Sync](F3_JIRA_SYNC.md) — основной sync
- [F7: Team Sync](F7_TEAM_SYNC_ATLASSIAN.md) — теперь вызывается из syncProject
- [F29: Workflow Configuration](F29_WORKFLOW_CONFIGURATION.md) — inline wizard в step 3
