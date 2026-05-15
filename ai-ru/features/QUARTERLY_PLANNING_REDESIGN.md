# Quarterly Planning — Redesign (Канбан Backlog → В квартале)

**Статус:** 📋 Спецификация (F-номер будет присвоен при имплементации)
**Дата:** 2026-05-15

## Контекст

Текущая страница Quarterly Planning (`QuarterlyPlanningLivePage` из F68_QUARTERLY_PLANNING_PRODUCTION) функционально полная, но «выбивается из общей концепции» и неочевидна пользователю. Главный сценарий — **решить, что взять в квартал** — реализован как дашборд с тремя табами (Projects / Readiness / Teams), что заставляет переключаться между экранами и не даёт прямого механизма принятия решения. Кроме того, страница использует много кастомных CSS-классов (`.qpp-*`) вместо компонентов дизайн-системы.

Цель редизайна — превратить страницу в **инструмент принятия решений**: канбан-доска, где пользователь физически переносит эпики из бэклога в квартал, видя в реальном времени, помещаются ли они в ёмкость команд. Изменения сразу записываются в Jira как quarter label.

## Принципиальные решения

| Решение | Выбор | Обоснование |
|---|---|---|
| Главный сценарий | Решить, что взять в квартал | Активное принятие решения, не пассивный дашборд |
| Механизм фиксации | Писать quarter label в Jira напрямую из UI | Jira — single source of truth, прямое действие быстрее, чем «откройте Jira и проставьте label» |
| Единица планирования | **Эпик** (проект — группировка) | Реалистично: проект может попасть в квартал частично |
| Метафора UI | **Канбан Backlog → В квартале** | Прямая визуальная метафора «решение = движение» |
| Поведение при перегрузе | Предупреждать, но пускать | Перегруз бывает осознанным (контракторы, выходы) |
| Эпики без оценок/команд | Показывать в Backlog с пометкой; в квартал пускать, но НЕ учитывать в capacity | Не блокируем планирование, явно показываем риск |
| Manual boost | На эпике (миграция с проекта) | Эпик — единица планирования, boost логично там же |

## Структура нового UI

### Header (sticky)

- **Селектор квартала** — общий компонент (`MultiSelectDropdown` без `multi` или подобный)
- **Capacity-bars по командам** — прогресс-бары с цветами: зелёный <80%, жёлтый 80–100%, красный >100%. Обновляются в реальном времени при перемещении эпика. Подпись: `Backend  ███████░░  72% (172/240d)`
- **Кнопка «Опубликовать → Jira (N)»** — появляется при наличии несохранённых изменений, N = счётчик delta

```
+--------------------------------------------------------------------+
| Quarterly Planning      2026 Q2 ▾      [Опубликовать → Jira (3)] |
+--------------------------------------------------------------------+
| Backend  ███████░░░  72% (172/240d)   ✓ ok                        |
| Frontend █████████▌ 95% (228/240d)   ⚠ near limit                 |
| QA       ██░░░░░░░░  22% ( 53/240d)   ✓ ok                        |
| Design   ███████████ 105% (212/200d)  ❌ overloaded               |
+--------------------------------------------------------------------+
```

### Две колонки

#### Слева — Backlog
- Сгруппирован по проекту (collapsible)
- Эпики отсортированы по `priorityScore` (RICE + boost) сверху вниз
- Фильтры/поиск над колонкой: поиск, фильтр по проекту/команде

#### Справа — В квартале
- Переключатель группировки: по команде / по проекту
- Counter внизу: «10 эпиков, 145 чел-дней»

#### Карточка эпика
- Иконка типа (`getIssueIcon` + `getIssueTypeIconUrl`)
- Имя эпика (ссылка на Jira)
- RICE score (`RiceScoreBadge`)
- Проект (ссылка на ProjectPage)
- Команды (`TeamBadge` × N) с днями: `BE 5 · FE 7 · QA 2`
- Boost-чип (`+15` / `-10`, если ≠ 0) — клик открывает inline-edit
- Warning-бейджи: `нет оценки`, `нет команды`, `перегруз FE`
- Действие: кнопка `→ взять` / `← вернуть` (drag&drop как follow-up)

```
+---------------------------------+--------------------------------+
| 🔍 Backlog (сортировка: RICE↓) | 📌 В квартале (10 эпиков, 145d)|
| [Фильтры: проект ▾, команда ▾] | [Группировать: проект | команда]|
+---------------------------------+--------------------------------+
| ▼ Project Auth (3 эпика)        | ▼ Backend (4 эпика, 72d)      |
|   ◆ Login redesign         92  |   ◆ New onboarding        87  |
|     Project Auth · boost +15   |     Onboarding · BE 4d        |
|     BE 5 · FE 7 · QA 2  →     |     ← вернуть                 |
|                                |                                |
|   ◆ SSO migration         65  |   ◆ Billing rework        81  |
|     ⚠ нет оценки           →  |     Billing · BE 6d           |
|                                |     ⚠ перегружает Design      |
+---------------------------------+--------------------------------+
```

## Что убираем из старой страницы

- 3 таба (Projects / Readiness / Teams) — всё на одном экране
- Step cards (Projects → Readiness → Team Impact) — концептуальная подсказка не нужна, метафора канбана говорит сама за себя
- Отдельный таб Readiness — readiness виден как badges на карточках эпиков
- Отдельный таб Teams — capacity-bars в шапке + группировка «по команде» в правой колонке
- 4 KPI-карточки (Projects in quarter / Ready to plan / Epics coverage / Teams involved) — заменяет capacity-bars

## Что сохраняем

- Все backend-расчёты capacity / demand / risk buffer
- Manual boost (переезжает на эпик)
- Quarter inheritance (эпик наследует quarter от проекта, если своего нет) — из F67
- Авторизация и multi-tenancy
- Существующие данные — миграция переносит, не теряем

## Соответствие дизайн-системе

### Заменить
- `.qpp-status-pill`, `.qpp-risk-pill`, `.qpp-quarter-pill` → `StatusBadge` + `QuarterPill` (если нужен новый, в едином стиле)
- `.qpp-chip` (кастомные фильтры) → существующий `FilterChips` / pill-dropdown
- `.qpp-select` → общий dropdown-компонент
- `.qpp-tabs` — удалить (нет табов)
- `SummaryMetricCard` (локальный) → удалить
- `.qpp-boost-input` → общий input-компонент или мини-popover

### Использовать
- `TeamBadge`, `RiceScoreBadge` (уже есть)
- `getIssueIcon` + `getIssueTypeIconUrl` (уже есть)
- `Modal` — для подтверждения «Опубликовать → Jira» со списком изменений
- Цветовые константы из `constants/colors.tsx` вместо хардкода (#172b4d, #2f6fed и пр.)

## Изменения в API и данных

### Новые endpoints

| Метод | Путь | Назначение |
|---|---|---|
| `GET` | `/api/planning/{quarter}/epics` | Список эпиков с priorityScore, capacity-вкладом, флагами `hasEstimate`, `hasTeamMapping` |
| `POST` | `/api/planning/epics/{epicKey}/quarter` | Body: `{ quarter: "2026Q2" \| null }`. Установить/снять quarter label в Jira атомарно |
| `POST` | `/api/planning/epics/{epicKey}/boost` | Body: `{ boost: -50..50 }`. Установить boost на эпик |

### Миграция БД (Flyway, tenant schema)

- `T{N}__epic_manual_boost.sql` — новая таблица `epic_manual_boost (epic_key, boost, updated_at, PRIMARY KEY (epic_key))`. Либо колонка `boost` в `epics` (предпочтительнее, если эта таблица уже есть)
- Перенос данных: каждый эпик проекта получает `boost = project_manual_boost.boost`. Старая таблица `project_manual_boost` помечается deprecated и удаляется отдельной миграцией после релиза

### Сервисный слой

- `QuarterlyPlanningService` — добавить методы `assignEpicToQuarter()`, `removeEpicFromQuarter()`, `setEpicBoost()`
- Использовать `JiraConfigResolver` (НЕ `JiraProperties`)
- Использовать `WorkflowConfigService.isEpic()` для проверки типа (NEVER хардкод `"Epic"`)
- Jira API: `PUT /rest/api/3/issue/{key}` с обновлением `fields.labels` (добавить/удалить quarter label)

### Frontend

**Новые компоненты:**
- `frontend/src/components/planning/EpicCard.tsx` — карточка эпика
- `frontend/src/components/planning/CapacityBars.tsx` — sticky-bars в шапке
- `frontend/src/components/planning/BacklogColumn.tsx` + `InQuarterColumn.tsx`
- `frontend/src/components/planning/PublishToJiraModal.tsx` — подтверждение со списком изменений

**Переписать:**
- `frontend/src/pages/QuarterlyPlanningPage.tsx` — основной layout (header + 2 колонки)
- `frontend/src/pages/QuarterlyPlanningPage.css` — выбросить все `.qpp-*`, переписать на BEM-структуру нового UI
- `frontend/src/api/quarterlyPlanning.ts` — добавить новые методы (`getEpicsForQuarter`, `assignEpicToQuarter`, `setEpicBoost`)

## Edge cases

| Случай | Поведение |
|---|---|
| Эпик без rough estimate | В Backlog с бейджем «нет оценки»; можно взять в квартал, но в capacity-bars не входит. Бар показывает `+3 not estimated` рядом с цифрой |
| Эпик без team mapping | Аналогично — бейдж «нет команды», не учитывается в capacity |
| Эпик перегружает команду | Карточка получает красный border-left + бейдж `перегрузка FE`, capacity-bar становится красным, но кнопка «взять» работает |
| Jira API упал при публикации | Modal показывает ошибку, локальное состояние НЕ применяется (атомарность), пользователь может повторить |
| Эпик уже имеет другой quarter в Jira (2026Q1) | На карточке бейдж `в 2026Q1`; при попытке взять в 2026Q2 — confirm-dialog «переместить из Q1 в Q2?» |
| Конфликт с реальностью Jira | Polling списка эпиков каждые N сек (или ручной refresh) + reconcile при публикации (предупредить, если label поменялся извне) |
| Boost вне диапазона | Backend валидация `boost ∈ [-50, 50]`, UI ограничивает input |

## Поэтапная реализация

Большая фича — рекомендуется разделить:

1. **Phase 1 — Backend и миграция** (1 итерация)
   - Миграция `epic_manual_boost` с переносом данных
   - Endpoints `GET /api/planning/{quarter}/epics`, `POST .../boost`, `POST .../quarter`
   - Сервисные методы + интеграция с Jira API
   - Unit/integration тесты

2. **Phase 2 — Frontend (канбан-доска)** (1–2 итерации)
   - Header + capacity-bars
   - Backlog + В квартале колонки (без drag&drop, с кнопками)
   - EpicCard со всеми бейджами
   - PublishToJiraModal
   - Удаление старых табов и `.qpp-*` стилей

3. **Phase 3 — Polish и follow-up** (отдельно)
   - Drag & drop между колонками
   - Bulk-операции
   - Audit log изменений плана
   - Сравнение «было / стало» перед публикацией

## Verification

1. **API уровень** (curl/Postman):
   - `GET /api/planning/2026Q2/epics` возвращает список с корректным `priorityScore`, capacity-вкладом, флагами `hasEstimate`, `hasTeamMapping`
   - `POST /api/planning/epics/PROJ-123/quarter` с `{"quarter":"2026Q2"}` → в Jira появляется label `2026Q2`. С `null` → label удаляется
   - `POST /api/planning/epics/PROJ-123/boost` с `{"boost":15}` → значение сохранено в БД, при чтении возвращается
2. **UI:**
   - Перемещение эпика → capacity-bars обновляются мгновенно (без перезагрузки страницы)
   - Кнопка «Опубликовать» → Modal со списком изменений → Confirm → API call → успех/ошибка с retry
   - Эпик без оценки виден в Backlog с бейджем «нет оценки», при перемещении в квартал capacity не растёт
3. **Реальный кейс на dev:**
   - Взять эпик из 2026Q2, удалить из квартала через UI → label исчезает в Jira (проверить через Jira UI)
   - Вернуть назад → label возвращается
4. **Тесты:**
   - `QuarterlyPlanningServiceTest` — `assignEpicToQuarter`, `removeEpicFromQuarter`, `setEpicBoost`
   - `QuarterlyPlanningControllerTest` — авторизация, валидация (`boost ∈ [-50, 50]`)
   - Frontend: рендеринг `EpicCard`, `CapacityBars`, корректные callbacks

## Связанные фичи

- **F55** — Quarterly Capacity Planning (базовая логика capacity/demand/RICE)
- **F67** — Quarter Label & Filter (quarter labels на Board/Projects, quarter inheritance от parent project)
- **F68** — Quarterly Planning Production (текущая live-страница, которую заменяем)

## Решённые open questions

### 1. Прототипная страница (`?mock=1`)
**Не актуально.** Прототип уже удалён в F68_QUARTERLY_PLANNING_PRODUCTION; в коде остался только `QuarterlyPlanningLivePage`, встроенный напрямую в `QuarterlyPlanningPage.tsx`. Редизайн просто переписывает страницу целиком.

### 2. Audit log плана
**Не делать на MVP.** Обоснование:
- Jira хранит полный issue activity log — кто и когда менял quarter label
- Для boost достаточно колонок `updated_at` / `updated_by` в `epic_manual_boost`
- Отдельный UI «лог решений на квартал» — продуктовая фича, добавим follow-up F-задачей, если реально понадобится

### 3. Конфликты с Jira — синхронизация
**Подход: refresh-on-demand + reconcile при публикации. БЕЗ автополлинга и БЕЗ WebSocket.** Обоснование:
- Квартальное планирование не требует real-time актуальности — это «сессионная» работа, не activity-stream
- WebSocket в проекте используется только для Planning Poker; разворачивать инфраструктуру ради одной страницы дорого

**Реализация:**
- **Refresh при фокусе вкладки** + ручная кнопка «Обновить» в шапке рядом с селектором квартала
- **Reconcile при публикации:** перед записью в Jira backend fetch'ит актуальное состояние эпиков. Если label у эпика в Jira уже не совпадает с базовым состоянием (которое было при загрузке) — `PublishToJiraModal` показывает diff: «PROJ-123 был 2026Q2, теперь в 2026Q1. Перезаписать / Пропустить / Отменить?»
- Это даёт основную защиту от конфликтов при минимальной сложности
