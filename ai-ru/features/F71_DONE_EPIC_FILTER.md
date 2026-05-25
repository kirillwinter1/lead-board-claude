# F71 — Фильтр закрытых эпиков на доске

**Дата:** 2026-05-25
**Версия:** 0.71.0
**Статус:** Реализовано

## Проблема

На главной доске показывались все эпики, включая закрытые (статус Done). Done эпики засоряли список активной работы и участвовали в автоскорринге (получали 0 баллов за статус, но всё равно занимали слот в ранжировании).

Существовавший toggle `hideDone` в `useBoardFilters` был client-side, не сохранялся между сессиями и был выключен по умолчанию — пользователю нужно было каждый раз включать его вручную.

## Решение

Серверная фильтрация в двух точках:

### 1. Автоскорринг — исключение всех Done эпиков

`AutoScoreService.recalculateAll()` и `AutoScoreService.recalculateForTeam(Long teamId)` пропускают эпики, у которых `workflowConfigService.isDone(status, type, projectKey) == true`. Автоскорринг ранжирует только активную работу.

### 2. Доска — 14-дневное окно

`BoardService.getBoard(...)` по умолчанию скрывает Done эпики, у которых `done_at < now() - 14 дней`. Недавно закрытые остаются видны (для ретро, демо, празднования).

Константа `DONE_EPIC_VISIBILITY_DAYS = 14` в `BoardService`. Не вынесена в БД/конфиг — это UX-параметр уровня доски, не Jira-конфиг.

### 3. Escape hatch

Query-параметр `?includeArchived=true` отключает 14-дневный фильтр, возвращая все Done эпики.

### 4. UI

- Toggle "Show archived" в `FilterPanel` (по умолчанию выключен, tooltip "Закрытые эпики старше 14 дней") переключает запрос на бэк.
- Эпик-строки с `epicDone === true` отображаются с `opacity: 0.6` (визуальная подсказка "недавно закрыт, но всё ещё виден").

## Затронутые файлы

**Backend:**
- `BoardNode.java` — добавлено поле `OffsetDateTime doneAt`
- `BoardService.java` — параметр `includeArchived`, фильтр старше 14 дней, константа `DONE_EPIC_VISIBILITY_DAYS = 14`, заполнение `doneAt` в `mapToNode`, обновлён cache key
- `BoardController.java` — query-параметр `@RequestParam(defaultValue="false") boolean includeArchived`
- `AutoScoreService.java` — инъекция `WorkflowConfigService`, фильтр Done в `recalculateAll()` и `recalculateForTeam()`

**Frontend:**
- `frontend/src/components/board/types.ts` — добавлено `doneAt?: string` в интерфейс `BoardNode`
- `frontend/src/hooks/useBoardData.ts` — параметр `useBoardData(includeArchived: boolean = false)`, передача в API
- `frontend/src/hooks/useBoardFilters.ts` — удалён `hideDone` state (заменён серверной фильтрацией)
- `frontend/src/components/board/FilterPanel.tsx` — переименован prop `hideDone` → `includeArchived`, label "Show archived"
- `frontend/src/pages/BoardPage.tsx` — локальный state `includeArchived`, передача в `useBoardData` и `FilterPanel`
- `frontend/src/components/board/BoardRow.tsx` — `opacity: 0.6` для строк где `isEpicRow && node.epicDone`

## Источник данных для "даты закрытия"

Используется существующее поле `jira_issues.done_at` (миграция V16). Оно заполняется автоматически:
- `StatusChangelogService.updateDoneAtIfNeeded()` при синке, когда статус переходит в Done;
- `ChangelogImportService.fixDoneAtFromChangelog()` ретроактивно из реального Jira changelog (V17).

Новая миграция не требуется.

## Тесты

**`BoardServiceTest`** — 6 новых unit-тестов:
- `shouldExposeDoneAtOnEpicNode` — `doneAt` пробрасывается на `BoardNode`
- `shouldExcludeDoneEpicsOlderThan14Days` — фильтр работает
- `shouldIncludeRecentlyDoneEpics` — недавние Done остаются
- `shouldIncludeArchivedWhenFlagSet` — `includeArchived=true` возвращает всё
- `shouldShowDoneEpicWithNullDoneAt` — legacy данные (null) показываются
- `shouldShowDoneEpicAtBoundary` — точная граница 14 дней включается

**`AutoScoreServiceTest`** — 2 новых unit-теста:
- `RecalculateAllTests.shouldSkipDoneEpics`
- `RecalculateForTeamTests.shouldSkipDoneEpicsForTeam`

## Edge cases

| Случай | Поведение |
|--------|-----------|
| Эпик переоткрыли (Done → In Progress) | `StatusChangelogService.updateDoneAtIfNeeded` уже обрабатывает: при возврате в Done `done_at` обновляется новым timestamp. Reopened эпик снова появляется. |
| Done эпик с `done_at = null` (legacy) | Показывается (защитный default — не прячем данные без timestamp) |
| Эпик с `done_at > now()` (будущая дата, clock skew) | Показывается (фильтр использует `isBefore`) |
| Граница 14 дней ровно | Эпик с `doneAt = now - 14d` показывается (фильтр `isBefore(now - 14d)`, не `isBeforeOrEqual`) |
| `includeArchived=true` с большим объёмом данных | Без пагинации (доска и так загружает всё) |
| Per-tenant маппинг статусов | Уже учтено: `workflowConfigService.isDone(status, type, projectKey)` per-project |
| Done эпик с активными subtasks | По `isDone()` это Done — скрывается через 14d. Доступен через `includeArchived` |
| Sort order архивных при `includeArchived=true` | Тот же: `manual_order` → `auto_score DESC`. Архивные имеют stale `auto_score` (не пересчитываются), типично оказываются внизу — это и нужно. |

## Семантика toggle

Старый `hideDone`: OFF = показать всё (включая Done) → клиентский фильтр.
Новый `includeArchived`: OFF = показать активные + недавно закрытые (14d), ON = показать всё.

## Спецификация и план

- Spec: `docs/superpowers/specs/2026-05-25-done-epic-filter-design.md` (локальный, не в git)
- Plan: `docs/superpowers/plans/2026-05-25-done-epic-filter-plan.md` (локальный, не в git)
