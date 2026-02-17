# Test Plan — Lead Board

**Версия:** 1.0
**Дата:** 2026-02-17

---

## 1. Стратегия: Экраны, а не фичи

Тестируем по **экранам (страницам)**, а не по фичам. Причины:

| По фичам | По экранам |
|----------|-----------|
| F31 "Dynamic Status Colors" — где тестировать? Она на Board, Timeline, Projects | Тестируем Board — проверяем ВСЁ что на нём: цвета, порядок, оценки, бейджи |
| F21 "Unified Planning" — чистый backend, нет своей страницы | Тестируется через Board и Timeline, где видны результаты |
| Одна фича может затрагивать 5 экранов | Один экран = один QA-прогон, один отчёт |

**Исключение:** cross-cutting модули (Sync, Auth, Config) тестируются отдельно — они влияют на всё.

---

## 2. Что тестируем

### 2.1 Экраны (14 страниц)

| # | Экран | Route | Endpoints | Сложность | Приоритет |
|---|-------|-------|-----------|-----------|-----------|
| S1 | **Board** | `/board` | 10 | Высокая | P0 |
| S2 | **Timeline** | `/board/timeline` | 8 | Высокая | P1 |
| S3 | **Team Metrics** | `/board/metrics` | 14 | Высокая | ✅ Done |
| S4 | **Projects** | `/board/projects` | 7 | Средняя | P1 |
| S5 | **Data Quality** | `/board/data-quality` | 1 | Низкая | P2 |
| S6 | **Teams** | `/board/teams` | 8 | Средняя | P2 |
| S7 | **Team Members** | `/board/teams/:id` | 6 | Средняя | P2 |
| S8 | **Member Profile** | `/board/teams/:id/member/:mid` | 4 | Низкая | P3 |
| S9 | **Competency Matrix** | `/board/teams/:id/competency` | 4 | Средняя | P2 |
| S10 | **Planning Poker** | `/board/poker` + `/room/:code` | 14 | Высокая | P3 (отложен) |
| S11 | **Settings** | `/board/settings` | 4 | Низкая | P3 |
| S12 | **Workflow Config** | `/board/workflow` | 14 | Высокая | P1 |
| S13 | **Setup Wizard** | (первый запуск) | 3 | Средняя | P2 |
| S14 | **Landing Page** | `/landing` | 0 | Низкая | P3 |

### 2.2 Cross-cutting модули (нет своей страницы)

| # | Модуль | Endpoints | Сложность | Приоритет |
|---|--------|-----------|-----------|-----------|
| X1 | **Sync (Jira)** | 4 + SyncService | Высокая | P0 |
| X2 | **Auth / RBAC** | 5 + SecurityConfig | Высокая | P1 |
| X3 | **AutoScore / Planning** | 10 + 3 сервиса | Высокая | P1 |
| X4 | **Calendar** | 6 | Низкая | P3 |
| X5 | **RICE Scoring** | 9 | Средняя | P1 |

---

## 3. Чек-листы по экранам

### S1: Board (P0)

**Фичи на экране:** F8, F10, F11, F15, F21, F31, F36, BF1
**Backend:** BoardService (621 LOC), BoardController, AutoScoreService
**Frontend:** BoardPage.tsx

**Функциональные проверки:**

- [ ] Загрузка доски — эпики группируются по статусам
- [ ] Фильтрация по команде (teamIds)
- [ ] Фильтрация по статусам
- [ ] Поиск (query) — поиск по ключу и названию
- [ ] Пагинация (page, size)
- [ ] Раскрытие эпика → стори → подзадачи (иерархия)
- [ ] AutoScore — бейдж приоритета на эпиках и сторях
- [ ] Score breakdown tooltip (7 факторов epic, 9 факторов story)
- [ ] Manual order — drag & drop эпиков и сторей
- [ ] Рекомендации порядка (стрелки ↑↓●)
- [ ] Rough estimates — редактирование оценок по ролям
- [ ] WIP Limits — подсветка превышения
- [ ] Статусные цвета из WorkflowConfig (F31)
- [ ] Бейдж проекта на эпиках (F35)
- [ ] RICE Score бейдж (F36)
- [ ] Кнопка Sync — ручная синхронизация
- [ ] Expected Done дата на эпиках
- [ ] Flagged индикатор на эпиках/сторях

**Edge cases:**
- [ ] Пустая доска (нет задач)
- [ ] Эпик без сторей
- [ ] Стори без подзадач
- [ ] Очень длинное название задачи
- [ ] Невалидный teamId в фильтре

**API тесты:**
- [ ] `GET /api/board` — happy path
- [ ] `GET /api/board?query=LB-1` — поиск по ключу
- [ ] `GET /api/board?teamIds=999` — несуществующая команда
- [ ] `GET /api/board/{key}/score-breakdown` — breakdown существующего эпика
- [ ] `GET /api/board/{key}/score-breakdown` — несуществующий ключ
- [ ] `PUT /api/epics/{key}/order` — изменение порядка
- [ ] `PUT /api/stories/{key}/order` — изменение порядка сторей

---

### S2: Timeline (P1)

**Фичи:** F14, F20, F21, F32
**Backend:** ForecastController, ForecastSnapshotController, UnifiedPlanningService
**Frontend:** TimelinePage.tsx

**Функциональные проверки:**

- [ ] Загрузка Timeline — Gantt-диаграмма эпиков
- [ ] Выбор команды
- [ ] Forecast vs Unified алгоритм
- [ ] Retrospective Timeline (фактический поток фаз из changelog)
- [ ] Snapshot comparison — выбор даты снэпшота
- [ ] Expected Done линия
- [ ] Цвета статусов (из WorkflowConfig)
- [ ] Tooltip с деталями при наведении
- [ ] Pipeline ролей: SA → DEV → QA (динамический порядок)

**Edge cases:**
- [ ] Команда без эпиков
- [ ] Эпик без оценок (нет subtask estimates)
- [ ] Нет снэпшотов

**API тесты:**
- [ ] `GET /api/planning/forecast?teamId=1`
- [ ] `GET /api/planning/unified?teamId=1`
- [ ] `GET /api/planning/retrospective?teamId=1`
- [ ] `GET /api/forecast-snapshots/dates?teamId=1`
- [ ] `GET /api/forecast-snapshots/unified?teamId=1&date=...`

---

### S4: Projects (P1)

**Фичи:** F35, F36
**Backend:** ProjectController, ProjectService, RiceController, ProjectAlignmentService
**Frontend:** ProjectsPage.tsx

**Функциональные проверки:**

- [ ] Список проектов — карточки
- [ ] Прогресс-бар (% done)
- [ ] Expected Done дата проекта
- [ ] Раскрытие → дочерние эпики
- [ ] Статусные бейджи (динамические цвета)
- [ ] RICE Score — оценка проекта
- [ ] RICE Form — создание/редактирование оценки
- [ ] Alignment рекомендации
- [ ] Estimate/Logged часы

**Edge cases:**
- [ ] Проект без дочерних эпиков
- [ ] Проект с RICE Score = 0
- [ ] Проект с незавершёнными эпиками в разных командах

**API тесты:**
- [ ] `GET /api/projects`
- [ ] `GET /api/projects/{key}` — деталь проекта
- [ ] `GET /api/projects/{key}` — несуществующий ключ
- [ ] `GET /api/projects/{key}/recommendations`
- [ ] `POST /api/rice/assessments` — создание оценки
- [ ] `GET /api/rice/assessments/{key}` — получение оценки

---

### S12: Workflow Config (P1)

**Фичи:** F17, F29
**Backend:** WorkflowConfigController (14 endpoints), MappingAutoDetectService
**Frontend:** WorkflowConfigPage.tsx

**Функциональные проверки:**

- [ ] Отображение ролей (SA, DEV, QA, ...)
- [ ] Редактирование ролей (CRUD)
- [ ] Отображение типов задач (Epic, Story, Sub-task, Bug, ...)
- [ ] Редактирование типов задач
- [ ] Pipeline статусов — карточки по этапам
- [ ] Перетаскивание статусов (drag & drop)
- [ ] Цвет статуса — ColorPicker
- [ ] Типы связей (link types)
- [ ] Auto-detect из Jira metadata
- [ ] Валидация конфигурации
- [ ] Количество задач по статусам

**Edge cases:**
- [ ] Удаление роли, которая используется в planning
- [ ] Дублирование имени статуса
- [ ] Пустой workflow (нет ролей/типов)

**API тесты:**
- [ ] `GET /api/admin/workflow-config` — полный конфиг
- [ ] `PUT /api/admin/workflow-config/roles` — обновление ролей
- [ ] `PUT /api/admin/workflow-config/statuses` — обновление статусов
- [ ] `POST /api/admin/workflow-config/validate` — валидация
- [ ] `POST /api/admin/workflow-config/auto-detect` — автодетект
- [ ] `GET /api/admin/jira-metadata/*` — метаданные Jira
- [ ] Доступ без ADMIN роли → 403

---

### S5: Data Quality (P2)

**Фичи:** F18
**Backend:** DataQualityController, DataQualityService (17 правил)
**Frontend:** DataQualityPage.tsx

**Функциональные проверки:**

- [ ] Список нарушений по команде
- [ ] Фильтрация по severity (ERROR, WARNING, INFO)
- [ ] Каждое из 17 правил — проверить что срабатывает корректно
- [ ] Ссылки на задачи в Jira
- [ ] Общий score качества данных

**Edge cases:**
- [ ] Команда без нарушений (идеальные данные)
- [ ] Все задачи с нарушениями

---

### S6: Teams (P2)

**Фичи:** F5, F6, F7
**Backend:** TeamController (8 endpoints)
**Frontend:** TeamsPage.tsx

**Функциональные проверки:**

- [ ] Список команд
- [ ] Создание команды
- [ ] Редактирование команды
- [ ] Удаление команды
- [ ] Синхронизация из Atlassian

**API тесты:**
- [ ] CRUD: POST, GET, PUT, DELETE
- [ ] Удаление команды с участниками

---

### S7: Team Members (P2)

**Фичи:** F5, F6
**Backend:** TeamController (members endpoints)
**Frontend:** TeamMembersPage.tsx

**Функциональные проверки:**

- [ ] Список участников команды
- [ ] Добавление участника
- [ ] Редактирование (роль, часы, грейд)
- [ ] Деактивация участника
- [ ] Planning Config (грейды, WIP, risk buffer)

---

### S9: Competency Matrix (P2)

**Фичи:** F34 (Competency)
**Backend:** CompetencyController (5 endpoints)
**Frontend:** TeamCompetencyPage.tsx

**Функциональные проверки:**

- [ ] Матрица компетенций по команде
- [ ] Оценка участника по компонентам (0-3)
- [ ] Bus Factor анализ
- [ ] Автоматическое назначение через AutoAssign

---

### S13: Setup Wizard (P2)

**Фичи:** F33
**Backend:** SyncController (issue-count, trigger)
**Frontend:** встроен в Layout

**Функциональные проверки:**

- [ ] Первый запуск → wizard показывается
- [ ] Step 1: выбор периода синхронизации
- [ ] Step 2: синхронизация из Jira
- [ ] Step 3: настройка workflow
- [ ] Step 4: готово
- [ ] Повторный вход → wizard НЕ показывается

---

### S8: Member Profile (P3)

**Фичи:** F30
**Проверки:** профиль участника, DSR тренд, задачи (completed/active/upcoming)

### S10: Planning Poker (P3 — отложен)

**Фичи:** F23
**Статус:** Отложен (известные баги с Jira)
**Проверки:** создание сессии, голосование, WebSocket, reveal, финальная оценка

### S11: Settings (P3)

**Фичи:** F27
**Проверки:** список пользователей, изменение роли, импорт changelog

### S14: Landing Page (P3)

**Проверки:** рендеринг, адаптивность, форма аудита

---

## 4. Cross-cutting модули

### X1: Sync (P0)

**Сервисы:** SyncService, ChangelogImportService, TeamSyncService
**Критичность:** источник данных для ВСЕГО продукта

**Проверки:**

- [ ] Инкрементальная синхронизация (updated >= lastSync)
- [ ] Cursor-based pagination
- [ ] Upsert logic (новые + обновлённые задачи)
- [ ] Иерархия: PROJECT → EPIC → STORY → SUBTASK
- [ ] IssueLinks парсинг (для Projects)
- [ ] StatusChangelog запись при смене статуса
- [ ] FlagChangelog запись при установке/снятии флага
- [ ] done_at обновление при переходе в Done
- [ ] Команды синхронизируются после задач
- [ ] Ошибка Jira API → graceful degradation (не ломает существующие данные)

**Тест-сценарии:**
- [ ] Первая синхронизация (пустая БД)
- [ ] Инкрементальная (изменилась 1 задача)
- [ ] Задача удалена в Jira
- [ ] Jira API недоступен
- [ ] Rate limiting (429)

---

### X2: Auth / RBAC (P1)

**Сервисы:** OAuthService, LeadBoardAuthenticationFilter, AuthorizationService
**SecurityConfig:** 4 уровня доступа

**Проверки:**

- [ ] OAuth flow: authorize → callback → session cookie
- [ ] Session expiration → redirect to login
- [ ] ADMIN endpoints → 403 для non-admin
- [ ] Authenticated endpoints → 401 без cookie
- [ ] Public endpoints → 200 без cookie
- [ ] RBAC: ADMIN, TEAM_LEAD, MEMBER, VIEWER — правильные ограничения
- [ ] Logout → cookie удалена → 401

**Тест-сценарии:**
- [ ] Каждая роль → попытка доступа к admin endpoint
- [ ] Expired session
- [ ] Invalid session cookie
- [ ] Concurrent sessions одного пользователя

---

### X3: AutoScore / Planning (P1)

**Сервисы:** AutoScoreCalculator, StoryAutoScoreService, UnifiedPlanningService, ForecastService
**Критичность:** ядро бизнес-логики

**Проверки:**

- [ ] AutoScore эпиков (7 факторов)
- [ ] AutoScore сторей (9 факторов)
- [ ] RICE boost (+15 баллов)
- [ ] Alignment boost (+10 баллов)
- [ ] Flagged penalty (-100 баллов)
- [ ] UnifiedPlanning — pipeline по ролям
- [ ] Expected Done расчёт
- [ ] Story Forecast — прогноз по сторям
- [ ] Recalculate — пересчёт по команде/эпику
- [ ] Грейды (Senior 0.8, Middle 1.0, Junior 1.5)

---

### X5: RICE Scoring (P1)

**Сервисы:** RiceTemplateService, RiceAssessmentService
**Controller:** RiceController (9 endpoints)

**Проверки:**

- [ ] Шаблоны (Business, Technical) — загрузка критериев
- [ ] Создание оценки — выбор ответов
- [ ] Расчёт: (R × I × C) / E
- [ ] Нормализация 0-100
- [ ] Effort auto (из subtask estimates)
- [ ] Effort priority: auto > T-shirt > manual
- [ ] Наследование: Project → Epic (RICE Score проекта → эпиков)
- [ ] Ranking — сортировка по RICE Score

---

## 5. Порядок тестирования

```
Wave 1 (Critical Path):
  S1 Board        — основной экран
  X1 Sync         — источник данных

Wave 2 (Business Logic):
  X3 AutoScore    — ядро расчётов
  X2 Auth/RBAC    — безопасность
  X5 RICE         — скоринг
  S12 Workflow    — конфигурация

Wave 3 (Screens):
  S2 Timeline     — визуализация планирования
  S4 Projects     — управление проектами

Wave 4 (Secondary):
  S5 Data Quality — качество данных
  S6 Teams        — управление командами
  S7 Members      — участники команд
  S9 Competency   — матрица компетенций
  S13 Wizard      — первичная настройка

Wave 5 (Low Priority):
  S8 Profile      — профиль участника
  S10 Poker       — покер (отложен)
  S11 Settings    — настройки
  S14 Landing     — лендинг
  X4 Calendar     — производственный календарь
```

---

## 6. Как тестировать (процесс для QA-агента)

Для каждого экрана/модуля `/qa` выполняет:

| Этап | Действие | Артефакт |
|------|----------|----------|
| 1 | Прочитать спецификации фич, входящих в экран | Чек-лист |
| 2 | Ревью unit-тестов: покрытие, качество, пробелы | Таблица покрытия |
| 3 | Запустить `./gradlew test` + `npm test` | Результат тестов |
| 4 | API testing через curl (все endpoints экрана) | Лог запросов/ответов |
| 5 | Проверить бизнес-логику: формулы, edge cases | Ручные проверки |
| 6 | Ревью frontend-кода: loading, error, race conditions | Список issues |
| 7 | Regression: не сломано ли остальное? | Результат тестов |
| 8 | QA Report | `testing/reports/YYYY-MM-DD_<SCREEN>.md` |

---

## 7. Метрики прогресса

| Метрика | Текущее | Цель |
|---------|---------|------|
| Экраны проверены QA | 1 / 14 (7%) | 14 / 14 (100%) |
| Cross-cutting проверены | 0 / 5 (0%) | 5 / 5 (100%) |
| Открытых багов | 9 | 0 |
| Unit-тесты backend | ~290 | ~400+ |
| Frontend тесты | ~240 | ~300+ |
| Integration тесты | ~20 | ~50+ |
