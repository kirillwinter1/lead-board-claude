# F42: Bug Management

## Статус: ЗАВЕРШЕНА

## Описание

Выделение багов в отдельную категорию `BUG` с настраиваемым SLA по приоритетам и двумя новыми Data Quality правилами.

## Что сделано

### Backend

1. **BoardCategory.BUG** — новое значение enum между STORY и SUBTASK
2. **V43 миграция** — таблица `bug_sla_config` с дефолтными SLA порогами:
   - Highest: 24ч, High: 72ч, Medium: 168ч (7д), Low: 336ч (14д), Lowest: 672ч (28д)
3. **WorkflowConfigService** — `isBug()`, `isStoryOrBug()`, `getBugTypeNames()`, BUG fallback на STORY маппинги статусов, `determinePhase()` без хардкода
4. **MappingAutoDetectService** — `detectBoardCategory()` определяет "bug"/"баг"/"дефект" как BUG (а не STORY)
5. **BugSlaConfigEntity + Repository** — JPA entity для таблицы `bug_sla_config`
6. **BugSlaService** — `checkSlaBreach()`, `checkStale()`, `getResolutionTimeHours()`, `getDaysSinceUpdate()`
7. **BugSlaController** — `GET /api/bug-sla`, `PUT /api/bug-sla/{priority}` (@PreAuthorize ADMIN/PM)
8. **DataQualityRule** — `BUG_SLA_BREACH` (ERROR), `BUG_STALE` (WARNING)
9. **DataQualityService.checkBug()** — все Story-проверки + SLA breach + staleness
10. **DataQualityController** — баги фильтруются через `isStoryOrBug()`, маршрутизация в `checkBug()` vs `checkStory()`
11. **BoardService** — баги включены в иерархию наравне со сторями
12. **StoryAutoScoreService** — `isBug()` через WorkflowConfigService, `recalculateAll()` включает BUG категорию

### Frontend

1. **WorkflowConfigContext** — `isBug()`, `isStoryOrBug()`
2. **WorkflowConfigPage** — BUG в BOARD_CATEGORIES, авто-сопоставление bug/баг/дефект → BUG
3. **BoardTable.tsx** — `isStoryOrBug()` вместо хардкода
4. **BoardRow.tsx** — `isStoryOrBug()` вместо хардкода
5. **PriorityCell.tsx** — `isBug()` вместо хардкода
6. **BugSlaSettingsPage** — таблица SLA с inline edit, маршрут `/board/bug-sla`
7. **AlertIcon.tsx + DataQualityPage.tsx** — labels для BUG_SLA_BREACH, BUG_STALE
8. **IssueTypeMappingDto** — добавлен тип 'BUG'

### Также обновлены

- IssueOrderService, UnifiedPlanningService, PokerController — `isStory()` → `isStoryOrBug()`
- Все тесты обновлены: BugSlaServiceTest (новый), MappingAutoDetectServiceTest, DataQualityServiceTest, StoryAutoScoreServiceTest, UnifiedPlanningServiceTest, BoardServiceTest, IssueOrderServiceTest

## API

### GET /api/bug-sla
Возвращает все SLA конфиги.

### PUT /api/bug-sla/{priority}
Обновляет SLA для приоритета. Требует роль ADMIN или PROJECT_MANAGER.
```json
{ "maxResolutionHours": 72 }
```

## Data Quality правила

| Правило | Severity | Описание |
|---------|----------|----------|
| BUG_SLA_BREACH | ERROR | Баг превысил SLA (время жизни > порога) |
| BUG_STALE | WARNING | Баг без обновлений > 14 дней |

## Нюансы

- **Статусные маппинги для BUG**: при отсутствии BUG-специфичных маппингов, fallback на STORY маппинги
- **Существующие баги**: останутся с boardCategory=STORY до перемаппинга через UI и ресинка
- **Миграция не делает UPDATE** — пользователь перемаппит типы через Workflow Config UI
