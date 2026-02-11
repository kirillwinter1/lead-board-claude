# F28. AI Simulation (Симуляция работы команды)

## Обзор

Автоматическая симуляция работы команды в Jira на основе плана из Lead Board. Использует OAuth-токены реальных пользователей для выполнения действий от их имени. Запускается ежедневно, имитирует реалистичную работу с небольшими случайными отклонениями.

Цель: тестирование функционала Lead Board на реалистичных данных без ручного труда.

---

## Принцип работы

```
┌─────────────┐     ┌──────────────┐     ┌───────────────┐     ┌──────────┐
│  Forecast   │ ──► │  Simulation  │ ──► │   Jira API    │ ──► │  Lead    │
│  (план на   │     │  Engine      │     │  (от имени    │     │  Board   │
│   сегодня)  │     │  + deviation │     │   юзеров)     │     │  (sync)  │
└─────────────┘     └──────────────┘     └───────────────┘     └──────────┘
```

1. **Читаем план** — forecast из UnifiedPlanningService (что должно происходить сегодня)
2. **Добавляем отклонения** — случайные ±1-3 дня, ±10-30% по времени
3. **Выполняем в Jira** — от имени assignee (OAuth токен из БД)
4. **Синхронизируем** — Lead Board подхватывает изменения при следующем sync

---

## Ежедневный цикл симуляции

### Что происходит при запуске (конец дня)

#### 1. Subtasks — основной уровень действий

| Действие | Условие | Jira API |
|----------|---------|----------|
| Начать работу | Предыдущий subtask assignee завершён (или первый в очереди) | `POST /transitions` → "В работе" |
| Списать время | Subtask в статусе "В работе" | `POST /worklog` (hoursPerDay из TeamMember) |
| Отправить на проверку | totalLogged >= effectiveHours (estimate × gradeCoeff × deviation) | `POST /transitions` → "Проверка" |
| Завершить | Subtask на проверке ≥ 1 дня | `POST /transitions` → "Готово" |

#### 2. Stories — автоматический переход

| Действие | Условие |
|----------|---------|
| → Analysis | Первый SA-subtask начат |
| → Analysis Review | Все SA-subtasks завершены |
| → Development | Первый DEV-subtask начат |
| → Dev Review | Все DEV-subtasks завершены |
| → Testing | Первый QA-subtask начат |
| → Done | Все subtasks завершены |

#### 3. Epics — автоматический переход

| Действие | Условие |
|----------|---------|
| → Developing | Первая story перешла в Analysis+ |
| → E2E Testing | Все stories завершены (упрощённо) |
| → Done | E2E Testing ≥ 2 дней |

---

## Модель работы и отклонений

### Начало работы

Subtask берётся в работу **сразу после завершения предыдущего** — без пауз и отклонений по дням. Цепочка непрерывная:

```
Subtask A (Готово) → Subtask B (В работе) — мгновенно
```

Порядок subtasks определяется pipeline SA → DEV → QA и forecast из планировщика.

### Списание времени

Базовое значение — hoursPerDay из TeamMember, но с отклонением ±30% для реалистичности и видимости колебаний в метриках эффективности:

```java
// Базовое списание = hoursPerDay из TeamMember
double baseHours = teamMember.getHoursPerDay();  // например 6.0

// Отклонение ±30% для реалистичных колебаний
double dailyDeviation = 1.0 + random(-0.3, +0.3);
double hoursToLog = baseHours * dailyDeviation;

// Не списывать больше чем осталось по estimate
hoursToLog = min(hoursToLog, remainingEstimateHours);

// Округление до 0.5ч
hoursToLog = round(hoursToLog * 2) / 2.0;
```

Это создаёт естественные колебания продуктивности (4.2ч, 7.8ч, 5.5ч...), которые будут видны в метриках эффективности по члену команды.

### Скорость выполнения (грейд-коэффициент)

Время на выполнение subtask рассчитывается с учётом грейда:

```java
// effectiveEstimate = originalEstimate × gradeCoefficient
// Senior (0.8): 8ч оценка → 6.4ч реально
// Middle (1.0): 8ч оценка → 8.0ч реально
// Junior (1.5): 8ч оценка → 12.0ч реально
double gradeCoeff = planningConfig.getGradeCoefficient(teamMember.getGrade());
double effectiveHours = subtask.getOriginalEstimateHours() * gradeCoeff;
```

Subtask завершается когда `totalLogged >= effectiveHours`.

### Отклонения (реалистичность)

Отклонения применяются к **скорости выполнения** — иногда задача занимает чуть больше или меньше effective estimate:

```java
// Отклонение от effective estimate: ±10-20%
double deviationFactor = 1.0 + random(-0.2, +0.2);
double actualHours = effectiveHours * deviationFactor;
```

Это приводит к естественному сдвигу всей цепочки: если один subtask занял больше — следующие начинаются позже.

| Паттерн | Вероятность | Описание |
|---------|-------------|----------|
| По плану | 70% | deviation ±5% |
| Быстрее | 15% | deviation -10-20% |
| Медленнее | 10% | deviation +10-20% |
| Значительно медленнее | 5% | deviation +20-40% |

---

## Использование OAuth токенов

### Выбор токена для действия

```java
// Для каждого subtask определяем assignee
String assigneeAccountId = subtask.getAssigneeAccountId();

// Ищем OAuth токен этого пользователя
OAuthTokenEntity token = oAuthTokenRepository.findByAtlassianAccountId(assigneeAccountId);

if (token != null && token.isValid()) {
    // Выполняем от имени assignee
    jiraClient.transitionIssue(subtask.getKey(), targetStatus, token.getAccessToken());
    jiraClient.addWorklog(subtask.getKey(), hours, token.getAccessToken());
} else {
    // Пользователь не авторизован — пропускаем, логируем
    log.warn("No valid token for user {}, skipping", assigneeAccountId);
    simulationLog.addSkipped(subtask.getKey(), "No OAuth token");
}
```

### Автообновление токенов

Перед каждым действием проверяем валидность токена. Если истёк — автоматический refresh через существующий `OAuthService.refreshToken()`.

---

## Расписание и запуск

### Автоматический (cron)

```yaml
simulation:
  enabled: false          # выключено по умолчанию (только pre-prod)
  cron: "0 0 19 * * MON-FRI"  # 19:00 каждый будний день
  team-ids: [1, 2]       # какие команды симулировать
  deviation:
    speed-factor: 0.2     # макс отклонение скорости (±%)
    on-track-chance: 0.70       # по плану (±5%)
    early-chance: 0.15          # быстрее (-10-20%)
    delay-chance: 0.10          # медленнее (+10-20%)
    severe-delay-chance: 0.05   # значительно медленнее (+20-40%)
```

### Ручной запуск

```
POST /api/simulation/run?teamId=1&date=2026-02-10
```

Можно указать конкретную дату для пошаговой симуляции (отладка).

### Dry Run

```
POST /api/simulation/dry-run?teamId=1&date=2026-02-10
```

Возвращает план действий без выполнения в Jira.

---

## Лог симуляции

Каждый запуск логируется для отладки и анализа:

```json
{
  "date": "2026-02-10",
  "teamId": 1,
  "actions": [
    {
      "issueKey": "LB-45",
      "action": "TRANSITION",
      "from": "Новое",
      "to": "В работе",
      "user": "Иванов А.",
      "deviation": "+1 day late"
    },
    {
      "issueKey": "LB-42",
      "action": "WORKLOG",
      "hours": 5.5,
      "user": "Петров И.",
      "remaining": "12h"
    },
    {
      "issueKey": "LB-40",
      "action": "TRANSITION",
      "from": "В работе",
      "to": "Готово",
      "user": "Сидоров В.",
      "deviation": "-2 days early"
    }
  ],
  "skipped": [
    {
      "issueKey": "LB-50",
      "reason": "No OAuth token for user X"
    }
  ],
  "summary": {
    "transitions": 5,
    "worklogs": 8,
    "skipped": 1,
    "totalHoursLogged": 42.5
  }
}
```

---

## Архитектура

### Backend

```
com.leadboard.simulation/
├── SimulationService          — Оркестрация: план → отклонения → действия
├── SimulationPlanner          — Чтение forecast, определение действий на сегодня
├── SimulationDeviation        — Генерация отклонений (рандом с паттернами)
├── SimulationExecutor         — Выполнение действий через Jira API (с OAuth)
├── SimulationLogger           — Логирование действий
├── SimulationController       — API: run, dry-run, logs
└── SimulationConfig           — Конфигурация (cron, deviation params)
```

### DB Schema

```sql
-- Миграция: V__create_simulation_logs.sql

CREATE TABLE simulation_logs (
    id          BIGSERIAL PRIMARY KEY,
    team_id     BIGINT NOT NULL REFERENCES teams(id),
    sim_date    DATE NOT NULL,                  -- какой день симулировали
    actions     JSONB NOT NULL,                 -- массив действий
    summary     JSONB NOT NULL,                 -- сводка
    status      VARCHAR(50) NOT NULL,           -- COMPLETED, PARTIAL, FAILED
    error       TEXT,
    started_at  TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    created_at  TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_sim_logs_team_date ON simulation_logs(team_id, sim_date DESC);
```

### API Endpoints

| Метод | Путь | Описание | Доступ |
|-------|------|----------|--------|
| POST | `/api/simulation/run` | Запуск симуляции `{teamId, date?}` | Admin |
| POST | `/api/simulation/dry-run` | Dry run (без действий в Jira) | Admin |
| GET | `/api/simulation/logs` | Логи симуляции `{teamId, from?, to?}` | Admin |
| GET | `/api/simulation/logs/{id}` | Детали запуска | Admin |
| GET | `/api/simulation/status` | Текущий статус (running/idle) | Admin |

---

## Безопасность

- **Только Admin** может запускать симуляцию
- **Выключено по умолчанию** (`simulation.enabled=false`)
- **Только pre-prod** — на проде не включать
- **Dry run** — всегда тестировать перед реальным запуском
- **Логирование** — все действия записываются для аудита
- **Rate limiting** — пауза между Jira API вызовами (avoid rate limits)

---

## План реализации (поэтапно)

### Этап 1: Planner + Dry Run
1. SimulationConfig — конфигурация
2. SimulationPlanner — чтение forecast, определение действий на день
3. SimulationDeviation — модель отклонений
4. SimulationController — dry-run endpoint
5. Тесты

### Этап 2: Executor + Jira интеграция
1. SimulationExecutor — выполнение через Jira API с OAuth токенами
2. Transitions (subtasks, stories, epics)
3. Worklogs (списание времени)
4. Автообновление токенов
5. Rate limiting между вызовами
6. Тесты

### Этап 3: Логирование + Расписание
1. SimulationLogger + simulation_logs таблица
2. Cron расписание
3. API для просмотра логов
4. Тесты

### Этап 4: Паттерны и настройка
1. Реалистичные паттерны (ранее завершение, задержки)
2. UI для просмотра логов (Admin panel)
3. Настройка deviation параметров через UI
4. Тесты
