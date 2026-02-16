# Архитектура Lead Board

## Обзор

Lead Board — SaaS для управления IT-доставкой поверх Jira. Монорепозиторий: Java 21 backend + React frontend.

## Backend (Spring Boot 3, Java 21)

### Пакетная структура

```
com.leadboard/
├── admin/          — Admin API для управления пользователями
├── audit/          — Заявки на аудит (AuditRequestController → Telegram)
├── auth/           — OAuth 2.0 (Atlassian 3LO), пользователи, токены, RBAC (AppRole, SecurityConfig)
├── board/          — Агрегация данных для доски (BoardService 621 LOC)
├── calendar/       — Производственный календарь РФ (xmlcalendar.ru)
├── competency/     — Матрица компетенций: оценки участников, bus-factor алерты
├── config/
│   ├── controller/ — WorkflowConfigController (14 endpoints), JiraMetadataController, PublicConfigController
│   ├── entity/     — ProjectConfigurationEntity, WorkflowRoleEntity, IssueTypeMappingEntity, StatusMappingEntity, LinkTypeMappingEntity, TrackerMetadataCacheEntity
│   ├── repository/ — Репозитории для config entities
│   ├── service/    — WorkflowConfigService, MappingAutoDetectService, MappingValidationService, JiraMetadataService
│   └── JiraProperties, WebConfig, RoughEstimateProperties
├── controller/     — BoardController, HealthController, ConfigController
├── epic/           — Rough estimates для эпиков (EpicController, EpicService)
├── forecast/       — Снэпшоты прогнозов (историческое хранение)
├── jira/           — Jira REST API клиент, модели данных (JiraIssue 464 LOC)
├── metrics/
│   ├── controller/ — TeamMetricsController (8 endpoints)
│   ├── entity/     — FlagChangelogEntity
│   ├── service/    — TeamMetricsService, VelocityService, EpicBurndownService, DsrService, ForecastAccuracyService, StatusChangelogService, FlagChangelogService
│   └── dto/        — DTO для метрик
├── planning/       — Ядро планирования:
│   ├── UnifiedPlanningService (838 LOC) — основной алгоритм
│   ├── AutoScoreCalculator (320 LOC) — приоритизация эпиков (7 факторов)
│   ├── StoryAutoScoreService (392 LOC) — приоритизация stories (9 факторов)
│   ├── StoryForecastService (464 LOC) — прогноз по stories с assignee capacity
│   ├── IssueOrderService — ручное упорядочивание эпиков и stories (drag & drop)
│   ├── IssueOrderController — API для manual_order
│   ├── ForecastService (274 LOC) — legacy прогнозирование
│   ├── ForecastController — API для forecast, wip-history, role-load, retrospective
│   ├── AutoScoreController — API для autoscore (global, per team, per epic)
│   ├── StoryController — API для stories
│   ├── RetrospectiveTimelineService — ретроспектива
│   ├── RoleLoadService — загруженность по ролям
│   ├── StoryPriorityService — приоритизация stories
│   ├── WipSnapshotService — снэпшоты WIP
│   └── AssigneeSchedule (197 LOC) — расписания исполнителей
├── poker/          — Planning Poker с WebSocket (real-time)
│   ├── controller/ — PokerController (13 endpoints)
│   └── service/    — PokerSessionService, PokerJiraService
├── quality/        — Data Quality: 17 правил проверки (DataQualityService 444 LOC)
├── simulation/     — AI Simulation: моделирование сценариев планирования
│   ├── SimulationController (5 endpoints, ADMIN only)
│   ├── SimulationService, SimulationExecutor, SimulationPlanner
│   └── DTO и entities для логов симуляций
├── status/         — StatusCategory enum (TODO/IN_PROGRESS/DONE)
├── sync/           — Синхронизация с Jira (SyncService 437 LOC, инкрементальная, ChangelogImportService)
├── team/           — CRUD команд/участников, MemberProfileService, TeamSyncService
└── telegram/       — Интеграция с Telegram (TelegramService)
```

### Ключевые сервисы

| Сервис | LOC | Ответственность |
|--------|-----|-----------------|
| UnifiedPlanningService | 838 | Единый алгоритм планирования: dynamic role pipeline, capacity, dependencies |
| BoardService | 621 | Агрегация Epic→Story→Subtask, прогресс, оценки, alerts |
| StoryForecastService | 464 | Story-level прогноз с учётом assignee и грейдов |
| DataQualityService | 444 | 17 правил проверки качества данных |
| SyncService | 437 | Инкрементальная синхронизация из Jira, status changelog |
| StoryAutoScoreService | 392 | AutoScore для stories: 9 факторов + топологическая сортировка |
| WorkCalendarService | 359 | Рабочие дни, праздники РФ, расчёт дат |
| WorkflowConfigService | ~350 | Динамическая конфигурация workflow: роли, типы, статусы, маппинги |
| PokerSessionService | 325 | Planning Poker: сессии, голосование, WebSocket |
| AutoScoreCalculator | 320 | AutoScore для эпиков: 7 факторов |
| MappingAutoDetectService | ~200 | Автодетект маппингов из Jira metadata |
| CompetencyService | ~150 | Матрица компетенций, bus-factor анализ |
| SimulationService | ~200 | Запуск и хранение результатов симуляций |
| MemberProfileService | ~150 | Профиль участника: задачи, DSR тренд |

### Entity (23+ сущностей)

| Entity | Таблица | Описание |
|--------|---------|----------|
| JiraIssueEntity | jira_issues | Кэш задач Jira (431 LOC) |
| TeamEntity | teams | Команды + planning_config (JSONB) |
| TeamMemberEntity | team_members | Участники: role, grade, hoursPerDay |
| UserEntity | users | Пользователи приложения |
| OAuthTokenEntity | oauth_tokens | OAuth токены Atlassian |
| StatusChangelogEntity | status_changelog | История переходов статусов |
| CalendarHolidayEntity | calendar_holidays | Праздники/выходные |
| ForecastSnapshotEntity | forecast_snapshots | Ежедневные снэпшоты прогнозов |
| WipSnapshotEntity | wip_snapshots | Снэпшоты WIP |
| PokerSessionEntity | poker_sessions | Сессии Planning Poker |
| PokerStoryEntity | poker_stories | Stories в Poker сессии |
| PokerVoteEntity | poker_votes | Голоса участников |
| JiraSyncStateEntity | jira_sync_state | Состояние синхронизации |
| ProjectConfigurationEntity | project_configurations | Конфигурация проекта (project_key) |
| WorkflowRoleEntity | workflow_roles | Роли pipeline (SA, DEV, QA, ...) |
| IssueTypeMappingEntity | issue_type_mappings | Маппинг типов задач → EPIC/STORY/SUBTASK/IGNORE |
| StatusMappingEntity | status_mappings | Маппинг статусов → TODO/IN_PROGRESS/DONE + фазы |
| LinkTypeMappingEntity | link_type_mappings | Маппинг типов связей → BLOCKS/RELATED/IGNORE |
| TrackerMetadataCacheEntity | tracker_metadata_cache | Кэш метаданных Jira |
| FlagChangelogEntity | flag_changelog | История изменений флагов (pause/unpause) |
| MemberCompetencyEntity | member_competencies | Оценки компетенций участников |
| IssueOrderEntity | issue_order | Ручной порядок эпиков/stories |
| SimulationLogEntity | simulation_logs | Логи симуляций |

### API Endpoints (23 контроллера)

| Контроллер | Endpoints | Путь |
|------------|-----------|------|
| BoardController | 2 | /api/board |
| ForecastController | 9 | /api/planning/* |
| AutoScoreController | 6 | /api/planning/autoscore/* |
| StoryController | 3 | /api/stories/* |
| TeamController | 14 | /api/teams/* |
| TeamMetricsController | 8 | /api/metrics/* |
| PokerController | 13 | /api/poker/* |
| CalendarController | 6 | /api/calendar/* |
| ForecastSnapshotController | 6 | /api/forecast-snapshots/* |
| EpicController | 2 | /api/epics/* |
| DataQualityController | 1 | /api/data-quality |
| SyncController | 4 | /api/sync/* |
| OAuthController | 5 | /oauth/*, /api/auth/* |
| HealthController | 1 | /api/health |
| ConfigController | 1 | /api/config/* |
| AdminController | 2 | /api/admin/* |
| IssueOrderController | 2 | /api/epics/*/order, /api/stories/*/order |
| WorkflowConfigController | 14 | /api/admin/workflow-config/* |
| JiraMetadataController | 3 | /api/admin/jira-metadata/* |
| PublicConfigController | 3 | /api/config/workflow/* |
| CompetencyController | 5 | /api/competencies/* |
| SimulationController | 5 | /api/simulation/* |
| AuditRequestController | 1 | /api/audit-requests |

## Frontend (React + Vite + TypeScript)

### Страницы (14)

| Страница | Путь | Описание |
|----------|------|----------|
| LandingPage | / | Маркетинговая лендинг-страница |
| BoardPage | /board | Основная доска Epic→Story→Subtask |
| TimelinePage | /timeline | Gantt-диаграмма с фазами по ролям |
| TeamMetricsPage | /metrics | Метрики команды: DSR, throughput, forecast accuracy |
| DataQualityPage | /data-quality | Отчёт о качестве данных |
| TeamsPage | /teams | Управление командами |
| TeamMembersPage | /teams/:id | Участники команды, planning config |
| MemberProfilePage | /teams/:teamId/members/:memberId | Профиль участника: задачи, DSR тренд |
| TeamCompetencyPage | /teams/:id/competency | Матрица компетенций команды |
| PlanningPokerPage | /poker | Лобби Planning Poker |
| PokerRoomPage | /poker/:id | Комната Poker с WebSocket |
| SettingsPage | /settings | Настройки приложения |
| WorkflowConfigPage | /workflow-config | Конфигурация workflow (роли, статусы, маппинги) |
| SetupWizardPage | /setup | Мастер первоначальной настройки (4 шага) |

### Структура компонентов

```
frontend/src/
├── api/            — 9 API клиентов (board, forecast, teams, metrics, poker, stories, epics, config, competency)
├── components/
│   ├── Layout.tsx           — Навигация + табы
│   ├── Modal.tsx            — Модальные окна
│   ├── MultiSelectDropdown.tsx
│   ├── board/               — FilterPanel, BoardTable, BoardRow, SortableEpicRow, SortableStoryRow,
│   │                          StatusBadge, RoleChips, EpicRoleChip, ProgressCell, PriorityCell,
│   │                          ExpectedDoneCell, StoryExpectedDoneCell, AlertIcon, StatusStylesContext
│   ├── competency/          — CompetencyRating
│   ├── metrics/             — DsrGauge, ThroughputChart, ForecastAccuracyChart, ForecastScatterPlot,
│   │                          VelocityChart, EpicBurndownChart, TimeInStatusChart, AssigneeTable, RoleLoadBlock
│   └── landing/             — HeroSection, DemoBoard, DemoMetrics, DemoTimeline, DemoRoleLoad,
│                              ExecutionSnapshot, AuditModal, LandingHeader, ICPSection, BaselineSection,
│                              FounderSection, MethodSection, AuditSection
├── contexts/
│   └── WorkflowConfigContext.tsx — Глобальный контекст workflow конфигурации
├── hooks/
│   ├── usePokerWebSocket.ts     — WebSocket для Poker
│   ├── useBoardData.ts          — Загрузка и кэширование данных доски
│   ├── useBoardFilters.ts       — Состояние фильтров доски
│   ├── useBoardForecasts.ts     — Прогнозы для доски
│   └── useTooltipPosition.ts    — Позиционирование tooltip
├── pages/          — 14 страниц (см. выше)
│   └── landing/    — LandingPage и связанные компоненты
├── icons/          — SVG иконки
├── App.tsx         — Роутинг
└── main.tsx        — Entry point
```

## Схема данных (ключевые связи)

```
project_configurations 1──N workflow_roles
                       1──N issue_type_mappings
                       1──N status_mappings
                       1──N link_type_mappings

teams 1──N team_members
  │
  │ (team_id)
  ▼
jira_issues (Epic → Story → Subtask через parent_key)
  │
  ├──N status_changelog (история переходов)
  ├──N flag_changelog (история флагов)
  ├──N forecast_snapshots (ежедневные прогнозы)
  └──N wip_snapshots (история WIP)

team_members 1──N member_competencies

poker_sessions 1──N poker_stories 1──N poker_votes

users 1──1 oauth_tokens
```

## Потоки данных

**Синхронизация:** Jira API → SyncService → JiraIssueEntity + StatusChangelog + FlagChangelog
**Планирование:** JiraIssueEntity → AutoScore → UnifiedPlanningService → ForecastResponse
**Метрики:** StatusChangelog + FlagChangelog + JiraIssueEntity → TeamMetricsService → Dashboard
**Poker:** Frontend ↔ WebSocket ↔ PokerSessionService ↔ JiraClient
**Workflow Config:** Jira Metadata → MappingAutoDetectService → WorkflowConfigService → все сервисы
