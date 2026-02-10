# F30. AI Digest

## Обзор

AI-генерируемые дайджесты с метриками, графиками и рекомендациями. Два типа:
- **Периодический** — недельный (MVP), позже месяц / квартал / спринт
- **Событийный** — по закрытию эпика (MVP), позже по закрытию проекта

Дайджесты сохраняются, доступны всем, отображаются на отдельной вкладке.

## Тип 1: Недельный дайджест (MVP)

### Содержание

| Блок | Данные | Источник |
|------|--------|----------|
| Выполнено | Закрытые эпики/стори за неделю, throughput | jira_issues, status_changelog |
| В работе | Текущий WIP, прогресс по эпикам | BoardService |
| Риски | Сдвиги forecast, превышение WIP, просроченные due dates | UnifiedPlanningService |
| Метрики | DSR, velocity тренд, forecast accuracy | TeamMetricsService |
| Data Quality | Количество проблем, критичные | DataQualityService |
| Рекомендации | AI-анализ: что приоритетно на следующей неделе | LLM |
| Графики | Throughput, Velocity, DSR Gauge, Burndown (снэпшоты PNG) | Сохранённые изображения |

### AI-саммари

LLM получает структурированные данные команды и генерирует:
- Краткое резюме недели (2-3 предложения)
- Ключевые достижения
- Проблемы и риски
- Рекомендации на следующую неделю

Язык: **русский**.

### Триггер

- **Автоматически:** воскресенье вечером (cron)
- **Вручную:** кнопка "Сгенерировать дайджест" (для пересоздания или нестандартного периода)

---

## Тип 2: Дайджест по закрытию эпика (MVP)

### Содержание

| Блок | Данные | Источник |
|------|--------|----------|
| Резюме эпика | Название, описание, команда, длительность | jira_issues |
| План vs Факт | Запланированные даты (forecast) vs фактические | forecast_snapshots, status_changelog |
| Forecast Accuracy | Насколько точным был прогноз | TeamMetricsService |
| DSR | Delivery Speed Ratio по эпику | TeamMetricsService |
| Участники | Кто работал, сколько залогировал | jira_issues (subtasks) |
| Timeline | Фактический timeline по фазам SA/DEV/QA | status_changelog |
| Графики | Epic Burndown (план vs факт), timeline (снэпшоты PNG) | Сохранённые изображения |
| AI-анализ | Выводы: что пошло хорошо, что можно улучшить | LLM |

### Триггер

- **Автоматически:** при переходе эпика в статус "Готово"

---

## Будущие расширения

| Расширение | Когда |
|------------|-------|
| Месячный / квартальный дайджест | После MVP |
| Дайджест по спринту | После внедрения спринтов |
| Дайджест по закрытию проекта | После F29 (Project-Level) |
| Данные EasyRetro в дайджесте | После внедрения EasyRetro |
| Выбор LLM в настройках | После экспериментов |

---

## Архитектура

### Backend

```
com.leadboard.digest/
├── DigestController          — REST API: CRUD дайджестов
├── DigestService             — Оркестрация: сбор данных → LLM → сохранение
├── DigestDataCollector       — Сбор сырых метрик за период
├── DigestEpicDataCollector   — Сбор данных по закрытому эпику
├── DigestChartService        — Генерация графиков в PNG (server-side)
├── DigestAiService           — Формирование промпта, вызов LLM, парсинг ответа
├── DigestScheduler           — Cron: воскресенье вечером
├── DigestEntity              — JPA entity
├── DigestChartEntity         — Графики (PNG)
├── DigestRepository          — JPA repository
├── LlmProvider               — Интерфейс для LLM
├── ClaudeProvider             — Реализация для Claude API
└── OpenAiProvider             — Реализация для OpenAI (опционально)
```

### LLM абстракция

```java
public interface LlmProvider {
    String generateSummary(String systemPrompt, String userData);
    String getModelName();
}
```

Конфигурация через `application.yml` / `.env`:
```yaml
digest:
  llm:
    provider: claude  # claude | openai | ...
    model: claude-sonnet-4-5-20250929
    api-key: ${LLM_API_KEY}
  schedule:
    cron: "0 0 21 * * SUN"  # воскресенье 21:00
```

### Графики — серверная генерация

Графики сохраняются как PNG снэпшоты, чтобы через время выглядели идентично.

**Варианты реализации (выберем при разработке):**
1. **JFreeChart** — Java-библиотека, генерация PNG на backend
2. **Frontend capture** — рендер на фронте через Recharts → html2canvas → отправка PNG на backend

### DB Schema

```sql
-- Миграция: V30__create_digests.sql

CREATE TABLE digests (
    id              BIGSERIAL PRIMARY KEY,
    team_id         BIGINT NOT NULL REFERENCES teams(id),
    type            VARCHAR(50) NOT NULL,       -- WEEKLY, EPIC_CLOSURE (позже: MONTHLY, QUARTERLY, SPRINT, PROJECT)
    period_start    DATE,                        -- начало периода (для периодических)
    period_end      DATE,                        -- конец периода
    epic_key        VARCHAR(50),                 -- для EPIC_CLOSURE
    title           VARCHAR(500) NOT NULL,       -- "Дайджест команды X за 3-9 февраля"
    summary_text    TEXT,                        -- AI-сгенерированный текст (markdown)
    raw_data        JSONB,                       -- сырые данные (метрики, throughput, etc.)
    status          VARCHAR(50) NOT NULL DEFAULT 'GENERATING',  -- GENERATING, READY, FAILED
    error_message   TEXT,                        -- если FAILED
    llm_model       VARCHAR(100),               -- какая модель использовалась
    llm_tokens_used INTEGER,                    -- потраченные токены
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE TABLE digest_charts (
    id          BIGSERIAL PRIMARY KEY,
    digest_id   BIGINT NOT NULL REFERENCES digests(id) ON DELETE CASCADE,
    chart_type  VARCHAR(100) NOT NULL,          -- THROUGHPUT, VELOCITY, DSR_GAUGE, BURNDOWN, TIMELINE
    title       VARCHAR(300),                   -- подпись графика
    image_data  BYTEA NOT NULL,                 -- PNG
    sort_order  INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_digests_team_date ON digests(team_id, created_at DESC);
CREATE INDEX idx_digests_type ON digests(type);
CREATE INDEX idx_digest_charts_digest ON digest_charts(digest_id);
```

### API Endpoints

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/api/digests?teamId={id}&type={type}` | Список дайджестов (без текста, пагинация) |
| GET | `/api/digests/{id}` | Полный дайджест с текстом |
| GET | `/api/digests/{id}/charts/{chartId}` | PNG графика |
| POST | `/api/digests/generate` | Ручная генерация `{teamId, type, periodStart?, periodEnd?, epicKey?}` |
| DELETE | `/api/digests/{id}` | Удалить дайджест (Admin) |

### Frontend

```
frontend/src/
├── pages/
│   └── DigestsPage.tsx         — Список дайджестов команды
│   └── DigestDetailPage.tsx    — Просмотр конкретного дайджеста
├── api/
│   └── digestApi.ts            — API клиент
└── components/
    └── digests/
        ├── DigestCard.tsx       — Карточка в списке (дата, тип, статус)
        ├── DigestSummary.tsx    — AI-текст (markdown render)
        ├── DigestMetrics.tsx    — Метрики в цифрах
        └── DigestCharts.tsx     — Графики (img из API)
```

**Роутинг:**
- `/digests` — список дайджестов (фильтр по команде, типу)
- `/digests/:id` — детальный просмотр

---

## Промпт для LLM (черновик)

### Недельный дайджест

```
Ты — аналитик IT-доставки. Составь недельный дайджест команды на русском языке.

Данные за неделю {periodStart} — {periodEnd}:

ВЫПОЛНЕНО:
- Закрытые эпики: {list}
- Закрытые стори: {count}
- Throughput: {value} стори/неделю

В РАБОТЕ:
- Активные эпики: {list with progress %}
- WIP: {current}/{limit}

МЕТРИКИ:
- DSR: {value} (тренд: {trend})
- Velocity: {logged}ч / {capacity}ч
- Forecast Accuracy: {value}%

РИСКИ:
- {list of risks}

DATA QUALITY:
- Проблем: {count}, критичных: {critical}

Напиши:
1. Краткое резюме недели (2-3 предложения)
2. Ключевые достижения
3. Проблемы и риски
4. Рекомендации на следующую неделю

Формат: markdown. Стиль: профессиональный, конкретный, без воды.
```

### Дайджест закрытия эпика

```
Ты — аналитик IT-доставки. Составь итоговый отчёт по закрытому эпику на русском языке.

ЭПИК: {key} — {summary}
Команда: {teamName}
Период: {startDate} — {endDate} ({totalWorkDays} рабочих дней)

ПЛАН vs ФАКТ:
- Forecast дата: {forecastDate}
- Фактическая дата: {actualDate}
- Отклонение: {deltaDays} дней

ОЦЕНКА vs ФАКТ:
- Original Estimate: {estimate}ч
- Time Spent: {spent}ч
- DSR: {dsr}

ПО ФАЗАМ:
- SA: план {saPlan}д / факт {saActual}д
- DEV: план {devPlan}д / факт {devActual}д
- QA: план {qaPlan}д / факт {qaActual}д

УЧАСТНИКИ:
{list: name, role, hoursLogged}

Напиши:
1. Краткое резюме (2-3 предложения)
2. Что пошло хорошо
3. Что можно улучшить
4. Выводы для будущих эпиков

Формат: markdown. Стиль: профессиональный, конструктивный.
```

---

## План реализации

### Этап 1: Недельный дайджест
1. DB миграция (digests, digest_charts)
2. DigestEntity, DigestChartEntity, DigestRepository
3. LlmProvider интерфейс + первая реализация
4. DigestDataCollector — сбор метрик за период
5. DigestChartService — генерация PNG
6. DigestService — оркестрация
7. DigestController — API
8. DigestScheduler — cron воскресенье
9. Frontend: DigestsPage, DigestDetailPage
10. Тесты

### Этап 2: Дайджест по эпику
1. DigestEpicDataCollector
2. Триггер при переходе в "Готово" (event listener в SyncService)
3. Промпт и данные для эпика
4. Расширение UI для типа EPIC_CLOSURE
5. Тесты

### Этап 3: Расширения
- Месяц / квартал
- Спринт (после внедрения спринтов)
- Проект (после F29)
- EasyRetro данные
