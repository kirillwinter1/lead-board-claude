# F81. Путь по статусам (тултип на бейдже «дней в статусе»)

**Версия:** 0.81.0 | **Дата:** 2026-07-01

При наведении на бейдж «Nд в статусе» ([[F79]] `StatusAgeBadge`) показывается богатый
тултип с хронологическим путём задачи по статусам: сколько задача пробыла в каждом
статусе — от стартового («Новая») до текущего. Работает для эпиков, историй, багов и
сабтасков (везде, где есть бейдж).

## Источник данных
- Путь строится из `status_changelog` (`from_status`/`to_status`, `transitioned_at`),
  заполняется `ChangelogImportService` из Jira changelog.
- **Названия статусов** — реальные имена Jira-workflow из changelog.
- **Цвета статусов** — из `StatusStylesContext` (`status_mappings`), через `StatusBadge`
  (правило дизайн-системы: статусы — только через `StatusBadge`).
- **Длительности** — из таймстемпов `transitioned_at` (не из `time_in_previous_status_seconds`,
  т.к. у первого перехода оно часто NULL).

## Логика построения сегментов (backend — `StatusHistoryService`)
Переходы задачи, отсортированные по `transitioned_at` ASC: `t1..tn`.
- **Стартовый сегмент:** статус = `t1.fromStatus`; длительность = `t1.transitionedAt − jira_created_at`.
- **Каждый переход `ti`:** вход в `ti.toStatus` в момент `ti.transitionedAt`; длительность =
  `t(i+1).transitionedAt − ti.transitionedAt` (для последнего = `now − tn.transitionedAt`).
- **Текущий сегмент** помечается `current: true` (его `toStatus` сверяется с `jira_issues.status`).
- Повторные статусы (reopen / возвраты) показываются как есть — честная хронология.
- Отрицательные/нулевые длительности схлопываются в 0 (кламп `max(0, …)`).

### Fallback (нет changelog — ~15% задач)
Если у задачи нет ни одного перехода → один сегмент: текущий статус из `jira_issues.status`,
длительность = `now − jira_created_at`, `current: true`.

## API
`GET /api/issues/{issueKey}/status-history` (авторизованный, tenant-aware).
```json
{
  "issueKey": "LB-339",
  "currentStatus": "Dev Review",
  "totalSeconds": 950400,
  "segments": [
    { "status": "New",         "durationSeconds": 86400,  "current": false },
    { "status": "Analysis",    "durationSeconds": 86400,  "current": false },
    { "status": "Development", "durationSeconds": 259200, "current": true }
  ]
}
```

## Frontend
- Новый компонент `StatusHistoryTooltip` (по образцу lazy-тултипа `PriorityCell`):
  портал, hover с debounce (~300 мс), спиннер при загрузке, кеш по `issueKey`.
- `StatusAgeBadge` оборачивается hover-триггером; вместо native `title` — тултип.
- Каждый сегмент: `StatusBadge status={...}` слева, длительность справа; текущий подсвечен
  (жирный + маркер «сейчас»). Вертикальный таймлайн-«конверт», итог «Всего: Nд» внизу.
- Формат длительности: ≥1д → «Nд», иначе ≥1ч → «Nч», иначе «<1ч».

## Данные / без миграций
Новых таблиц/колонок нет — читаем существующий `status_changelog` + `jira_issues`.

## Тесты
- Backend (`StatusHistoryServiceTest`): линейный путь, текущий сегмент = `now − last`,
  стартовый сегмент от `jira_created_at`, reopen (повтор статуса), fallback без changelog,
  кламп отрицательных длительностей.
- Frontend (`StatusHistoryTooltip.test.tsx`): рендер сегментов, подсветка текущего,
  форматирование длительности, состояние загрузки.

## НЕ в F81
- Автор перехода (`author_account_id` есть, но по решению — только статус + длительность).
- Даты входа в статус в тултипе (только длительности).
- Агрегация повторных статусов (показываем хронологию как есть).
