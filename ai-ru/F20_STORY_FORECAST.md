# F20. Story-Level Planning с Assignee-based Capacity Allocation

**Статус:** 🚧 В разработке (Phase 1-3 из 6 завершены)
**Дата начала:** 2026-01-25

## Цель

Реализовать детальное планирование stories с учетом назначения на участников команды, их роли, грейда и capacity. Stories должны показывать точную расчетную дату завершения на основе:
- Назначенного исполнителя (assignee)
- Capacity исполнителя (hoursPerDay / grade coefficient)
- Очереди работы исполнителя
- Зависимостей между stories (blocks/is-blocked-by)
- Приоритета (AutoScore)

## Реализованные фазы (3/6)

### ✅ Phase 1: Database & Sync
**Статус:** Завершена (2026-01-25)

**Изменения:**
- **Migration V14**: Добавлены поля `assignee_account_id`, `assignee_display_name`, `started_at` в таблицу `jira_issues`
- **JiraIssue.JiraUser**: Класс для парсинга assignee из Jira API
- **JiraClient**: Добавлено поле "assignee" в запрос к Jira API
- **SyncService**:
  - Парсинг и сохранение assignee из Jira
  - Определение `started_at` для stories в статусе "In Progress"
  - Helper `isInProgressStatus()` для определения активных статусов

**Результат:**
- Assignee успешно синкается из Jira (проверено на 2 issues с assignee="Kirill Reshetov")
- Данные доступны в БД для использования в forecast

### ✅ Phase 2: Story Forecast Service
**Статус:** Завершена (2026-01-25)

**Компоненты:**
- **StoryForecastService.java** - основной сервис планирования stories
- **buildAssigneeSchedules()** - построение capacity schedules для каждого team member
- **calculateStorySchedules()** - основной алгоритм расчета расписания
- **findBestAssignee()** - автоназначение на свободных members

**Алгоритм:**
1. Получить stories epic'а, отсортированные по AutoScore с учетом dependencies
2. Построить assignee schedules с tracking nextAvailableDate
3. Для каждой story:
   - Определить assignee (из Jira или auto-assign)
   - Рассчитать remaining work = estimate - timeSpent
   - Проверить dependencies (blocked-by stories)
   - Найти earliest start = max(assignee available, dependencies met, epic start)
   - Рассчитать duration = remainingHours / assignee.effectiveCapacity
   - Установить startDate, endDate
   - Обновить assignee.nextAvailableDate

**Capacity calculation:**
- Effective capacity = hoursPerDay / gradeCoefficient
- Grade coefficients:
  - Senior: 0.8x (делает 1 MD за 0.8 дня)
  - Middle: 1.0x (базовый)
  - Junior: 1.5x (делает 1 MD за 1.5 дня)
- Пример: Senior (6 hrs/day, coeff 0.8) → 7.5 effective hrs/day

**Edge cases:**
- **Unassigned stories**: auto-assign на earliest available member с matching role (SA/DEV/QA)
- **No estimate**: workDays = 0, показывается как завершенная
- **Blocked stories**: startDate откладывается до завершения blockers
- **Assignee не в team**: fallback к role capacity pool

### ✅ Phase 3: API Layer
**Статус:** Завершена (2026-01-25)

**Endpoints:**

```
GET /api/planning/epics/{epicKey}/story-forecast?teamId={teamId}
```

**Response:**
```json
{
  "epicKey": "LB-95",
  "epicStartDate": "2026-01-25",
  "stories": [
    {
      "storyKey": "LB-210",
      "storySummary": "Implement user auth",
      "assigneeAccountId": "70121:b40ff773-75a6-4521-b351-6b0114b87dd4",
      "assigneeDisplayName": "Kirill Reshetov",
      "startDate": "2026-01-27",
      "endDate": "2026-01-30",
      "workDays": 3.5,
      "isUnassigned": false,
      "isBlocked": false,
      "blockingStories": [],
      "autoScore": 76.0,
      "status": "Analysis"
    }
  ],
  "assigneeUtilization": {
    "70121:b40ff773-75a6-4521-b351-6b0114b87dd4": {
      "displayName": "Kirill Reshetov",
      "role": "DEV",
      "workDaysAssigned": 12.5,
      "effectiveHoursPerDay": 6.00
    }
  }
}
```

**DTOs:**
- `StoryForecastResponse` - основной response
- `StoryScheduleDto` - расписание отдельной story
- `AssigneeUtilizationDto` - утилизация каждого assignee

**Результат:**
- API работает корректно
- Возвращает расписание для всех stories epic'а
- Включает utilization metrics для assignees

## Оставшиеся фазы (3/6)

### 📋 Phase 4: Board Integration
**Задачи:**
- Обновить BoardService - использовать story forecast для expectedDone
- Добавить assignee в BoardNode DTO
- Обновить StoryExpectedDoneCell - показывать assignee
- Тестировать Board UI

### 📋 Phase 5: Timeline Integration
**Задачи:**
- Обновить forecast.ts - добавить getStoryForecast()
- Добавить StorySchedule mode toggle
- Создать StoryBar component с assignee coloring
- Обновить StoryTooltip - показывать assignee и даты
- Добавить unassigned indicator
- Тестировать Timeline UI

### 📋 Phase 6: Testing & Documentation
**Задачи:**
- End-to-end тестирование с реальными данными
- Тестировать edge cases (unassigned, blocked, in progress)
- Performance testing (100+ stories)
- Unit tests для StoryForecastService (10+ тестов)
- Интеграционные тесты для API
- Обновить user documentation

## Технические детали

### Database Schema (V14)

```sql
ALTER TABLE jira_issues
  ADD COLUMN assignee_account_id VARCHAR(255),
  ADD COLUMN assignee_display_name VARCHAR(255),
  ADD COLUMN started_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_jira_issues_assignee
  ON jira_issues(assignee_account_id)
  WHERE assignee_account_id IS NOT NULL;
```

### Ключевые классы

**Backend:**
- `StoryForecastService` - основной сервис планирования
- `StoryForecastResponse` - DTO для API response
- `JiraIssueEntity` - расширен полями assignee

**Зависимости:**
- `StoryDependencyService` - topological sort с учетом dependencies
- `WorkCalendarService` - расчет workdays с учетом праздников
- `TeamService` - конфигурация команды (grade coefficients)

### Алгоритм в деталях

1. **Получить данные:**
   - Epic и его stories
   - Team configuration (grade coefficients, WIP limits)
   - Team members (jiraAccountId, role, grade, hoursPerDay)

2. **Построить assignee schedules:**
   - Для каждого member: effectiveHoursPerDay = hoursPerDay / gradeCoefficient
   - nextAvailableDate = today

3. **Сортировка stories:**
   - Topological sort по dependencies (StoryDependencyService)
   - Внутри layers - сортировка по AutoScore DESC

4. **Для каждой story:**
   - **Assignee selection:**
     - Если assignee указан в Jira и есть в team → использовать
     - Иначе auto-assign на earliest available member с matching role
   - **Remaining work:**
     - remainingHours = (originalEstimate - timeSpent) / 3600
     - Если <= 0 → story считается завершенной
   - **Dependencies check:**
     - dependenciesMetDate = max(blocker.endDate) для всех blockers
   - **Start date:**
     - earliestStart = max(assignee.nextAvailableDate, dependenciesMetDate, epicStartDate)
     - startDate = findNextWorkday(earliestStart)
   - **Duration:**
     - workDays = remainingHours / assignee.effectiveHoursPerDay
     - endDate = addWorkdays(startDate, workDays)
   - **Update assignee schedule:**
     - assignee.nextAvailableDate = addWorkdays(endDate, 1)

5. **Calculate utilization:**
   - Для каждого assignee суммировать workDaysAssigned

### Тестирование API

```bash
# Test story forecast
curl 'http://localhost:8080/api/planning/epics/LB-95/story-forecast?teamId=3' | jq

# Expected response:
# - epicKey, epicStartDate
# - stories[] with startDate, endDate, assignee, workDays
# - assigneeUtilization{} with workDaysAssigned per assignee
```

## Метрики успеха

**Backend (достигнуто):**
- ✅ Assignee синкается из Jira (2/228 issues have assignee)
- ✅ Migration V14 применена успешно
- ✅ StoryForecastService компилируется без ошибок
- ✅ API возвращает корректный JSON response
- ⏳ Unit tests (0/10+ planned)

**UI (не реализовано):**
- ⏳ Board показывает assignee для каждой story
- ⏳ Board показывает expectedDone из story forecast
- ⏳ Timeline имеет Story Schedule mode
- ⏳ Stories на Timeline окрашены по assignee
- ⏳ Tooltip показывает assignee и даты

**Business value (частично):**
- ✅ Видна capacity utilization каждого member
- ✅ Учитываются grade coefficients при планировании
- ⏳ Визуализация bottlenecks (overloaded assignees)
- ⏳ Раннее выявление blocked stories

## Риски и митигации

| Риск | Вероятность | Митигация |
|------|-------------|-----------|
| Assignee не в team roster | Средняя | Fallback к role capacity pool, логировать warning |
| Много unassigned stories | Высокая | Auto-assign на earliest available member |
| Circular dependencies | Низкая | StoryDependencyService детектит циклы |
| Performance с 100+ stories | Средняя | Кэширование forecast, индексы на assignee |

## Следующие шаги

1. **Phase 4-5: UI Integration** (2-3 дня)
   - Board integration - показ assignee и forecast dates
   - Timeline Story Schedule mode
   - Assignee coloring на Timeline

2. **Phase 6: Testing & Docs** (1-2 дня)
   - Unit tests для StoryForecastService
   - Integration tests для API
   - User documentation

3. **Оптимизации:**
   - Кэширование forecast results
   - Batch API для multiple epics
   - Real-time updates при изменении assignee

## Связанные фичи

- **F19. Story AutoScore** - используется для сортировки stories
- **F13. Epic Autoplanning** - epic-level forecast остается параллельно
- **F16. Pipeline WIP** - WIP limits могут влиять на story scheduling
- **F17. Status Mapping** - определение статусов для started_at
