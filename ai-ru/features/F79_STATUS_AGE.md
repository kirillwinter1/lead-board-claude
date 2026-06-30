# F79. Дней в статусе + «зависшие эпики»

**Версия:** 0.79.0 | **Дата:** 2026-06-28

Бейдж «Nд в статусе» у задачи с подсветкой по порогу. Показывается на Board (рядом
с бейджами проекта/квартала), на карточках Matrix и в рекомендациях.

## Логика (вся на backend — `com.leadboard.status.StatusAgeService`)
- `daysInStatus` = сегодня − вход в текущий статус: последний `status_changelog.transitioned_at`
  с `to_status == текущий статус`, иначе `jira_created_at`.
- `statusAgeLevel` ∈ {NORMAL, WARNING, CRITICAL}. Цвет — **только для активных** статусов
  (категория ≠ NEW/бэклог и ≠ DONE); бэклог и done всегда NORMAL (серый, число всё равно видно).
- Пороги по типу (warn/crit, дней): **Сабтаск 3/7 · Стори 7/14 · Эпик 21/45 · Баг 3/7 · Project 21/45**.
- **Зависший эпик:** активный эпик, у которого по поддереву (стори + сабтаски) нет ни
  списаний времени (`issue_worklogs`), ни переходов статуса за период — порог бездействия
  **14/30 дней**. Уровень эпика = худший из (возраст статуса, зависание); причина — в тултип.
- `statusAgeReason` — текст тултипа для WARNING/CRITICAL.

## Контракт (на каждой задаче)
`daysInStatus: Integer|null`, `statusAgeLevel: String`, `statusAgeReason: String|null`.
Добавлено в `BoardNode`, `MatrixCardDto`, `RecCard`, `StoryRec`.

## Frontend
- `components/StatusAgeBadge.tsx` — единый бейдж (число + цвет из `STATUS_AGE_COLORS`).
- Размещён в `BoardRow` (`.name-row-labels`), `MatrixCard`, карточках `MatrixRecommendations`.

## Данные
Точность зависит от импорта `status_changelog` (`POST /api/sync/import-changelogs`) и
`issue_worklogs` (`POST /api/sync/import-worklogs`); без них — фолбэк на дату создания.

## НЕ в F79
Конфигурируемые пороги в UI, история/тренд возраста, алерты/нотификации.
