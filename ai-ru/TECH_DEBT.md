# Технический долг Lead Board

> Последнее обновление: 2026-03-09

## Backend

### God-классы (требуют декомпозиции)

| Класс | LOC | Проблема | Рекомендация |
|-------|-----|----------|--------------|
| UnifiedPlanningService | 1399 | Алгоритм планирования, capacity, scheduling, early exit — всё в одном | Выделить CapacityCalculator, ScheduleBuilder, DependencyResolver |
| SyncService | 818 | Sync + status changelog + done_at detection + worklog sync + per-project auto-detect | Выделить StatusChangeDetector, WorklogSyncService |
| BoardService | 733 | Агрегация, маппинг, сортировка, алерты, cache | Выделить BoardAggregator, BoardMapper |
| DataQualityService | 562 | 17+ правил проверки в одном классе | Паттерн Strategy: по правилу на класс или группировка |
| StoryForecastService | 478 | Story прогноз + assignee scheduling | Выделить AssigneeAllocator |

### Дублирование кода

| Что дублируется | Где встречается | Рекомендация |
|-----------------|-----------------|--------------|
| Grade коэффициенты (SENIOR 0.8, MIDDLE 1.0, JUNIOR 1.5) | UnifiedPlanningService, StoryForecastService, ForecastService, QuarterlyPlanningService | Единый метод в Grade enum или GradeService |
| Парсинг дат из Jira | SyncService (parseLocalDate, parseOffsetDateTime), TeamMetricsService, UnifiedPlanningResult | Утилитный класс JiraDateParser |
| ~~Проверка статусов (isDone, isInProgress)~~ | ~~BoardService, ForecastService, DataQualityService, SyncService~~ | ~~Используется WorkflowConfigService, но не везде~~ Решено: WorkflowConfigService используется во всех 16+ сервисах (58 вызовов) |
| ~~Расчёт прогресса (logged/estimate)~~ | ~~BoardService, UnifiedPlanningService, StoryForecastService~~ | Решено: расчёт только в BoardService, другие сервисы работают с иными абстракциями |
| ~~Списки типов задач~~ | ~~AutoScoreCalculator, StatusMappingService, SyncService~~ | Решено: динамическая конфигурация через WorkflowConfigService (F29) |

### Отсутствующие абстракции

- Нет интерфейсов для ключевых сервисов (сложно тестировать и подменять)
- Нет общего Result/Either типа для обработки ошибок планирования (есть DTO-результаты: UnifiedPlanningResult, RetrospectiveResult, ValidationResult — но не функциональные типы)
- Нет EventBus для оповещения о sync/recalculate

### ~~Inconsistent error handling~~

Решено (F46 Security Hardening):
- GlobalExceptionHandler с обработчиками: IllegalArgumentException (400), TeamNotFoundException (404), MethodArgumentNotValid (400), MissingParam (400), AccessDenied (403), Exception (500 generic)
- Ошибки API не раскрывают внутренние детали (`"An internal error occurred"`)

### Безопасность (CRITICAL)

- [x] ~~OAuth токены хранятся без шифрования в БД~~ — Решено (F62): AES-256 шифрование через JPA AttributeConverter + Spring Security Crypto
- [x] ~~Нет CSRF защиты~~ — CSRF отключён намеренно (token-based auth для REST API, см. SecurityConfig)
- [x] ~~UNIQUE constraint для team_members может быть нарушен~~ — Решено: conditional unique index `WHERE active = TRUE`

### ~~Производительность (HIGH)~~

Решено:
- [x] ~~N+1 запросы при загрузке Board (stories -> subtasks)~~ — `findByParentKeyIn()` batch loading используется (BoardService:107-115)
- [x] ~~Нет индексов для частых запросов метрик~~ — 30+ индексов: idx_jira_issues_team_done, idx_changelog_transitioned_at, idx_forecast_snapshots_team_date и др.
- [x] ~~Memory leak в sync locks~~ — SyncService не использует explicit locks (не воспроизводится)

## Frontend

### Большие компоненты (требуют декомпозиции)

| Компонент | LOC | Рекомендация |
|-----------|-----|--------------|
| WorkflowConfigPage | 2145 | Выделить ProjectSelector, IssueTypeMappings, StatusMappings, RoleMappings |
| TimelinePage | 1959 | Выделить GanttChart, GanttRow, GanttControls, SummaryPanel |
| ProjectTimelinePage | 1016 | Выделить ProjectGanttChart, ProjectGanttRow |
| PokerRoomPage | 895 | Выделить StoryList, VotingPanel, ResultsPanel |

~~BoardPage (1798 LOC)~~ — Решено: декомпозирован до 184 LOC (useBoardData, useBoardFilters, useBoardForecasts хуки + FilterPanel, BoardTable, BoardRow и др.)

### Дублирование кода

| Что дублируется | Где встречается | Рекомендация |
|-----------------|-----------------|--------------|
| formatDate (8+ реализаций) | EpicBurndownChart, ForecastAccuracyChart, StoryExpectedDoneCell, ExpectedDoneCell, AbsenceTimeline, DataQualityPage, ProjectsPage, MemberProfilePage, TimelinePage | Выделить `src/utils/dateFormatting.ts` |
| API error handling | Разные паттерны: `.catch(() => {})`, `.catch(err => console.error(...))`, `.catch(err => alert(...))` | Единый error interceptor для axios + общий обработчик |
| ~~Цвета статусов~~ | ~~BoardPage, TimelinePage, DataQualityPage~~ | Решено: StatusStylesContext + StatusBadge повсеместно (F31/F39) |
| ~~Tooltip positioning логика~~ | ~~BoardPage, TimelinePage~~ | Решено: общий хук `useTooltipPosition()` (src/hooks/useTooltipPosition.ts) |
| ~~DSR/severity/chart цвета в 6+ файлах~~ | ~~DsrBreakdownChart, ForecastAccuracyChart, AssigneeTable, VelocityChart, DsrGauge, DataQualityPage~~ | Решено: `constants/colors.ts` — единый источник (2026-03-08) |
| ~~Inline SeverityBadge~~ | ~~DataQualityPage~~ | Решено: `components/SeverityBadge.tsx` — shared component (2026-03-08) |
| ~~Микс RU/EN в UI~~ | ~~21 файл: метрики, таймлайн, доска, графики~~ | Решено: все продуктовые экраны переведены на EN (2026-03-08) |

### Отсутствующее

- [ ] Error Boundaries — при ошибке в рендере падает всё приложение
- [ ] Кеширование запросов (нет React Query / SWR — raw axios + useState)
- [x] ~~Frontend тесты (0 тестов)~~ — Решено: 18+ файлов тестов
- [x] ~~Нет TypeScript strict mode~~ — Решено: `"strict": true` в tsconfig.json
- [ ] Нет общего Loading/Error state компонента (inline `{loading && <div>Loading...</div>}` на каждой странице)
- [ ] Нет стейт-менеджмента (useState + Context API, но без библиотеки — 388 useState по кодовой базе)

### ~~Рефакторинг метрик (из плана работ)~~

Решено: TeamMetricsService декомпозирован на отдельные сервисы:
- DsrService — расчёт DSR
- VelocityService — скорость команды
- ForecastAccuracyService — точность прогнозов
- EpicBurndownService — агрегация burndown
- StatusChangelogService — логирование переходов
- BugMetricsService — метрики багов (F42/F43)

## Приоритеты исправления

1. ~~**CRITICAL**: Шифрование OAuth токенов в БД~~ — Решено (F62)
2. **HIGH**: Error Boundaries, formatDate утилита, API error handling
3. **MEDIUM**: Декомпозиция god-классов (UnifiedPlanningService 1399 LOC, TimelinePage 1959 LOC, WorkflowConfigPage 2145 LOC), устранение дублирования grade коэффициентов
4. **LOW**: React Query / кеширование, стейт-менеджмент, Loading/Error компоненты
