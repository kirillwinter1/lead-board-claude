# Технический долг Lead Board

## Backend

### God-классы (требуют декомпозиции)

| Класс | LOC | Проблема | Рекомендация |
|-------|-----|----------|--------------|
| UnifiedPlanningService | 838 | Алгоритм планирования, capacity, scheduling — всё в одном | Выделить CapacityCalculator, ScheduleBuilder, DependencyResolver |
| BoardService | 621 | Агрегация, маппинг, сортировка, алерты | Выделить BoardAggregator, BoardMapper |
| StoryForecastService | 464 | Story прогноз + assignee scheduling | Выделить AssigneeAllocator |
| DataQualityService | 444 | 17 правил проверки в одном классе | Паттерн Strategy: по правилу на класс или группировка |
| SyncService | 437 | Sync + status changelog + done_at detection | Выделить StatusChangeDetector |
| JiraIssueEntity | 431 | Слишком много полей и ответственностей | Разделить на JiraIssueEntity + JiraIssueMetadata |

### Дублирование кода

| Что дублируется | Где встречается | Рекомендация |
|-----------------|-----------------|--------------|
| Проверка статусов (isDone, isInProgress) | BoardService, ForecastService, DataQualityService, SyncService | Уже есть StatusMappingService, но не везде используется |
| Расчёт прогресса (logged/estimate) | BoardService, UnifiedPlanningService, StoryForecastService | Выделить ProgressCalculator |
| Парсинг дат из Jira | SyncService, JiraIssue, MetricsService | Утилитный класс JiraDateParser |
| Списки типов задач (Аналитика, Разработка, Тестирование) | AutoScoreCalculator, StatusMappingService, SyncService | Единый enum или конфиг |
| Grade коэффициенты | UnifiedPlanningService, StoryForecastService, ForecastService | Единый GradeService |

### Отсутствующие абстракции

- Нет интерфейсов для ключевых сервисов (сложно тестировать и подменять)
- Нет общего Result/Either типа для обработки ошибок планирования
- Нет EventBus для оповещения о sync/recalculate

### Inconsistent error handling

- Где-то RuntimeException, где-то кастомные исключения
- Нет глобального ExceptionHandler для REST API
- Ошибки Jira API не всегда корректно обрабатываются

### Безопасность (CRITICAL)

- [ ] OAuth токены хранятся без шифрования в БД
- [ ] Нет CSRF защиты
- [ ] UNIQUE constraint для team_members может быть нарушен

### Производительность (HIGH)

- [ ] N+1 запросы при загрузке Board (stories → subtasks)
- [ ] Нет индексов для частых запросов метрик
- [ ] Memory leak в sync locks (при ошибках не освобождаются)

## Frontend

### Большие компоненты (требуют декомпозиции)

| Компонент | LOC | Рекомендация |
|-----------|-----|--------------|
| BoardPage | 1798 | Выделить BoardFilters, BoardTable, BoardRow, EpicRow, StoryRow |
| TimelinePage | 1126 | Выделить GanttChart, GanttRow, GanttControls, SummaryPanel |

### Дублирование кода

| Что дублируется | Где встречается |
|-----------------|-----------------|
| formatDate (форматирование дат) | BoardPage, TimelinePage, MetricsPage |
| Цвета статусов | BoardPage, TimelinePage, DataQualityPage |
| Tooltip positioning логика | BoardPage, TimelinePage |
| API error handling | Каждый API вызов обрабатывает ошибки по-своему |

### Отсутствующее

- [ ] Error Boundaries — при ошибке падает всё приложение
- [ ] Кеширование запросов (нет React Query / SWR)
- [ ] Frontend тесты (0 тестов)
- [ ] Нет TypeScript strict mode
- [ ] Нет общего Loading/Error state компонента
- [ ] Нет стейт-менеджмента (всё в useState, props drilling)

## Приоритеты исправления

1. **CRITICAL**: Шифрование OAuth токенов, CSRF
2. **HIGH**: N+1 запросы, Error Boundaries, React Query
3. **MEDIUM**: Декомпозиция god-классов, устранение дублирования
4. **LOW**: Strict mode, стейт-менеджмент, frontend тесты
