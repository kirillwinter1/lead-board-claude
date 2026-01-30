# Архитектура Lead Board

## Обзор

Lead Board — SaaS для управления IT-доставкой поверх Jira. Монорепозиторий: Java 21 backend + React frontend.

## Backend (Spring Boot 3, Java 21)

### Пакетная структура

```
com.leadboard/
├── auth/           — OAuth 2.0 (Atlassian 3LO), пользователи, токены
├── board/          — Агрегация данных для доски (BoardService 621 LOC)
├── calendar/       — Производственный календарь РФ (xmlcalendar.ru)
├── config/         — JiraProperties, WebConfig, RoughEstimateProperties
├── controller/     — BoardController, HealthController, ConfigController
├── epic/           — Rough estimates для эпиков
├── forecast/       — Снэпшоты прогнозов (историческое хранение)
├── jira/           — Jira REST API клиент, модели данных (JiraIssue 464 LOC)
├── metrics/        — Командные метрики: throughput, lead/cycle time, DSR (Delivery Speed Ratio), forecast accuracy
├── planning/       — Ядро планирования:
│   ├── UnifiedPlanningService (838 LOC) — основной алгоритм
│   ├── AutoScoreCalculator (320 LOC) — приоритизация эпиков (7 факторов)
│   ├── StoryAutoScoreService (392 LOC) — приоритизация stories (9 факторов)
│   ├── StoryForecastService (464 LOC) — прогноз по stories с assignee capacity
│   ├── IssueOrderService — ручное упорядочивание эпиков и stories (drag & drop)
│   ├── IssueOrderController — API для manual_order (PUT /api/epics/{key}/order, PUT /api/stories/{key}/order)
│   ├── ForecastService (274 LOC) — legacy прогнозирование
│   └── AssigneeSchedule (197 LOC) — расписания исполнителей
├── poker/          — Planning Poker с WebSocket (real-time)
├── quality/        — Data Quality: 17 правил проверки (DataQualityService 444 LOC)
├── status/         — Configurable Status Mapping (StatusMappingService 300 LOC)
├── sync/           — Синхронизация с Jira (SyncService 437 LOC, инкрементальная)
└── team/           — CRUD команд/участников, planning config
```

### Ключевые сервисы

| Сервис | LOC | Ответственность |
|--------|-----|-----------------|
| UnifiedPlanningService | 838 | Единый алгоритм планирования: SA→DEV→QA pipeline, capacity, dependencies |
| BoardService | 621 | Агрегация Epic→Story→Subtask, прогресс, оценки, alerts |
| StoryForecastService | 464 | Story-level прогноз с учётом assignee и грейдов |
| DataQualityService | 444 | 17 правил проверки качества данных |
| SyncService | 437 | Инкрементальная синхронизация из Jira, status changelog |
| StoryAutoScoreService | 392 | AutoScore для stories: 9 факторов + топологическая сортировка |
| WorkCalendarService | 359 | Рабочие дни, праздники РФ, расчёт дат |
| PokerSessionService | 325 | Planning Poker: сессии, голосование, WebSocket |
| AutoScoreCalculator | 320 | AutoScore для эпиков: 7 факторов |
| StatusMappingService | 300 | Маппинг Jira статусов → TODO/IN_PROGRESS/DONE + фазы SA/DEV/QA |

### Entity (13 сущностей)

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

### API Endpoints (96 endpoints в 15 контроллерах)

| Контроллер | Endpoints | Путь |
|------------|-----------|------|
| BoardController | 2 | /api/board |
| ForecastController | 7 | /api/planning/* |
| AutoScoreController | 6 | /api/planning/autoscore/* |
| StoryController | 3 | /api/stories/* |
| TeamController | 14 | /api/teams/* |
| TeamMetricsController | 8 | /api/metrics/* |
| PokerController | 13 | /api/poker/* |
| CalendarController | 6 | /api/calendar/* |
| ForecastSnapshotController | 6 | /api/forecast-snapshots/* |
| EpicController | 2 | /api/epics/* |
| DataQualityController | 1 | /api/data-quality |
| SyncController | 2 | /api/sync/* |
| OAuthController | 5 | /oauth/*, /api/auth/* |
| HealthController | 1 | /api/health |
| ConfigController | 1 | /api/config/* |

## Frontend (React + Vite + TypeScript)

### Страницы (8)

| Страница | Путь | Описание |
|----------|------|----------|
| BoardPage | / | Основная доска Epic→Story→Subtask (1798 LOC) |
| TimelinePage | /timeline | Gantt-диаграмма с фазами SA/DEV/QA (1126 LOC) |
| TeamMetricsPage | /metrics | Метрики команды: DSR, throughput, forecast accuracy |
| DataQualityPage | /data-quality | Отчёт о качестве данных |
| TeamsPage | /teams | Управление командами |
| TeamMembersPage | /teams/:id | Участники команды, planning config |
| PlanningPokerPage | /poker | Лобби Planning Poker |
| PokerRoomPage | /poker/:id | Комната Poker с WebSocket |

### Структура компонентов

```
frontend/src/
├── api/            — 8 API клиентов (board, forecast, teams, metrics, poker, stories, epics, config)
├── components/
│   ├── Layout.tsx      — Навигация + табы
│   ├── Modal.tsx       — Модальные окна
│   ├── MultiSelectDropdown.tsx
│   └── metrics/        — MetricCard, ThroughputChart, DsrGauge, ForecastAccuracyChart, etc.
├── hooks/
│   └── usePokerWebSocket.ts — WebSocket для Poker
├── pages/          — 8 страниц (см. выше)
├── icons/          — SVG иконки
├── App.tsx         — Роутинг
└── main.tsx        — Entry point
```

## Схема данных (ключевые связи)

```
teams 1──N team_members
  │
  │ (team_id)
  ▼
jira_issues (Epic → Story → Subtask через parent_key)
  │
  ├──N status_changelog (история переходов)
  ├──N forecast_snapshots (ежедневные прогнозы)
  └──N wip_snapshots (история WIP)

poker_sessions 1──N poker_stories 1──N poker_votes

users 1──1 oauth_tokens
```

## Потоки данных

**Синхронизация:** Jira API → SyncService → JiraIssueEntity + StatusChangelog
**Планирование:** JiraIssueEntity → AutoScore → UnifiedPlanningService → ForecastResponse
**Метрики:** StatusChangelog + JiraIssueEntity → TeamMetricsService → Dashboard
**Poker:** Frontend ↔ WebSocket ↔ PokerSessionService ↔ JiraClient
