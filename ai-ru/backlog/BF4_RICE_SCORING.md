# BF4. RICE Scoring

## Обзор

Конфигурируемая система оценки бизнес-ценности проектов и эпиков. Каждый параметр RICE состоит из настраиваемых подкритериев с баллами. Оценку дают PM или PO. RICE влияет на AutoScore и приоритизацию.

---

## Формула

```
RICE Score = (R × I × C) / E
```

| Параметр | Описание | Тип значения |
|----------|----------|-------------|
| **R** — Reach | Охват: на кого и сколько влияет | Сумма баллов подкритериев |
| **I** — Impact | Влияние: бизнес-эффект | Сумма баллов подкритериев |
| **C** — Confidence | Уверенность в оценке | Множитель (0.4-1.0) |
| **E** — Effort | Затраты: трудозатраты | T-shirt → автозамена на реальную оценку |

---

## Параметры

### Reach (охват) — составной, настраиваемый

Сумма баллов по всем подкритериям. Подкритерии настраиваются админом.

**Шаблон по умолчанию (Business):**

| Подкритерий | Тип | Варианты | Баллы |
|-------------|-----|----------|-------|
| Тип фичи | single | Продуктовая (переиспользуемая) / Специфичная | +3 / +1 |
| Кол-во пользователей | single | < 100 / 100-500 / 500-3000 / +1 за каждые 3000 | +1 / +3 / +5 / +1 |
| Кол-во клиентов/команд | single | < 5 / 5-20 / 20-50 / 50+ | +1 / +2 / +3 / +5 |

### Impact (влияние) — составной, настраиваемый

Сумма баллов по всем подкритериям.

**Шаблон по умолчанию (Business):**

| Подкритерий | Тип | Варианты | Баллы |
|-------------|-----|----------|-------|
| Инициатор запроса | single | Внешний клиент / Внутренний | +3 / +1 |
| Тип задачи | multi | Регуляторное требование / Импортозамещение / Развитие продукта / Снижение рисков | +5 / +5 / +1 / +3 |
| Соответствие целям | multi | Цели команды / Цели компании | +1 / +5 |
| Экономия FTE (руб/мес) | single | Нет / < 100K / 100-500K / 500K-1M / 1-3M / > 3M | 0 / +1 / +2 / +3 / +4 / +5 |
| Финансовые потери при неисполнении | single | Нет / < 100K / 100-500K / 500K-1M / 1-3M / > 3M | 0 / +1 / +2 / +3 / +4 / +5 |
| Лояльность пользователей | multi | Удобство использования / Ускорение процессов / Сокращение ошибок | +5 / +2 / +2 |
| Окупаемость | single | > 1 года / < 1 года / < 6 мес | +1 / +3 / +5 |

### Confidence (уверенность) — простой множитель

Single-select:

| Уровень | Множитель | Описание |
|---------|-----------|----------|
| High | 1.0 | Есть данные, метрики подтверждены |
| Medium | 0.8 | Есть данные, но не по всем критериям |
| Low | 0.6 | Оценки предположительные |
| Very Low | 0.4 | Данные отсутствуют или неподтверждены |

### Effort (затраты) — гибридный

**Ранние стадии (нет оценки):** ручной T-shirt размер

| Размер | Значение E |
|--------|-----------|
| S (Small) | 1 |
| M (Medium) | 2 |
| L (Large) | 4 |
| XL (Extra Large) | 8 |

**После появления оценки:** автоматическая подстановка

```
Приоритет: детальная оценка (из subtasks) → rough estimate → T-shirt (ручной)
```

Конвертация: суммарная оценка в person-months:
```
E = totalEstimateHours / (8 часов × 20 рабочих дней)
```

Минимальное значение E = 0.5 (защита от деления на очень маленькое число).

---

## Шаблоны RICE (Business vs Technical)

### Концепция

Бизнесовые и технические доработки оцениваются по **разным шаблонам** с разными подкритериями, но результаты нормализуются для сравнения.

### Шаблон Business (по умолчанию)

Подкритерии Reach и Impact описаны выше.

### Шаблон Technical

**Reach (Technical):**

| Подкритерий | Тип | Варианты | Баллы |
|-------------|-----|----------|-------|
| Scope влияния | single | Один сервис / Несколько сервисов / Вся система | +1 / +3 / +5 |
| Частота проблемы | single | Редко / Еженедельно / Ежедневно | +1 / +3 / +5 |

**Impact (Technical):**

| Подкритерий | Тип | Варианты | Баллы |
|-------------|-----|----------|-------|
| Влияние на стабильность | single | Низкое / Среднее / Высокое / Критичное | +1 / +3 / +5 / +10 |
| Влияние на производительность | single | Нет / Небольшое / Существенное | 0 / +2 / +5 |
| Ускорение разработки | single | Нет / Немного / Существенно | 0 / +2 / +5 |
| Устранение техдолга | single | Косметический / Архитектурный / Критичный | +1 / +3 / +5 |
| Риск безопасности | single | Нет / Низкий / Высокий | 0 / +3 / +5 |

### Нормализация для сравнения

Внутри каждого шаблона RICE Score нормализуется в диапазон 0-100:

```
normalizedScore = (rawScore - minPossible) / (maxPossible - minPossible) × 100
```

`minPossible` / `maxPossible` рассчитываются из конфигурации шаблона (min/max сумма всех подкритериев).

Дополнительно: Admin может настроить **strategic weight** для каждого шаблона (например, Business ×1.0, Technical ×0.8) для тонкой балансировки.

---

## Применение оценки

### Кто оценивает

| Роль | Может оценивать |
|------|----------------|
| PROJECT_MANAGER | ✅ Проекты и эпики |
| PRODUCT_OWNER | ✅ Проекты и эпики |
| ADMIN | ✅ Всё |
| TEAM_LEAD | ❌ |
| MEMBER | ❌ |

### Что оценивается

- **Project** (задача типа Project в Jira) — основной уровень оценки
- **Epic без проекта** — оценивается самостоятельно

### Наследование

```
Если эпик входит в проект:
    эпик.riceScore = проект.riceScore (наследуется от родителя)
Если эпик без проекта:
    эпик.riceScore = эпик.собственный riceScore
```

Собственная оценка эпика внутри проекта игнорируется (используется проектная).

### Рекомендуемый статус для оценки

- **Рекомендуется:** BUSINESS_REQUIREMENTS (для проектов) или соответствующий ранний статус для эпиков
- **Можно:** в любом статусе
- **Data Quality alert:** если проект/эпик перешёл в PLANNING или дальше без RICE-оценки

---

## Влияние на AutoScore

### Текущие факторы AutoScore

| Фактор | Макс баллов | Источник |
|--------|-------------|----------|
| Status | -5 to +30 | Позиция в workflow |
| Jira Priority | 0-20 | Приоритет из Jira |
| Due Date | 0-25 | Экспоненциальный рост к дедлайну |
| Progress | 0-10 | Прогресс (logged/estimate) |
| Size | -5 to +5 | Размер (меньше = выше) |
| Age | 0-5 | Возраст задачи |
| **RICE** | **0-15** | **Новый (BF4)** |
| **Alignment** | **0-10** | **Новый (BF5, cross-team)** |

**Было:** макс ~95 баллов. **Стало:** макс ~120 баллов.

### RICE boost — расчёт

```
riceBoost = (normalizedRiceScore / 100) × MAX_RICE_WEIGHT
```

- `normalizedRiceScore` — нормализованный 0-100 (из effective RICE)
- `MAX_RICE_WEIGHT` — конфигурируемый (по умолчанию **15 баллов**)
- `effectiveRiceScore` — собственный или унаследованный от проекта

### Примеры

| RICE normalized | Boost | Пояснение |
|----------------|-------|-----------|
| 100 | +15.0 | Топ бизнес-ценность |
| 70 | +10.5 | Высокая ценность |
| 50 | +7.5 | Средняя |
| 20 | +3.0 | Низкая |
| 0 | 0 | Минимальная ценность |
| Не заполнен | 0 | Нейтрально (без штрафа) |

### Alignment boost (BF5, отдельный фактор)

```
alignmentBoost = min(delayDays × 1.0, 10)
```

Эпик, отстающий от среднего по проекту на N дней, получает boost до +10 баллов.

### Поведение при отсутствии RICE

Эпики без RICE-оценки получают riceBoost = **0** (нейтральный, не штрафуются). Это стимулирует заполнение RICE: оценённые эпики получают преимущество, но неоценённые не наказываются.

---

## Data Quality правила

| Правило | Severity | Условие |
|---------|----------|---------|
| RICE отсутствует | WARNING | Проект/эпик в статусе PLANNING+ без RICE |
| RICE неполный | INFO | Заполнены не все подкритерии |
| Effort устарел | INFO | T-shirt размер при наличии реальной оценки (автозамена не сработала) |

---

## Роль Product Owner (PO)

### RBAC

Новая роль: `PRODUCT_OWNER`

На одном уровне с PM и Team Lead:

```
ADMIN
├── PROJECT_MANAGER
├── PRODUCT_OWNER
├── TEAM_LEAD
MEMBER
VIEWER
```

### Права PO

| Действие | PO |
|----------|-----|
| RICE-оценка проектов и эпиков | ✅ |
| Просмотр проектов и метрик | ✅ |
| Просмотр доски и timeline | ✅ |
| Управление проектами | ❌ |
| Управление командами | ❌ |
| Admin-функции | ❌ |

---

## Архитектура

### DB Schema

```sql
-- Миграция: V__create_rice_scoring.sql

-- Шаблоны RICE
CREATE TABLE rice_templates (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,      -- "Business", "Technical"
    code            VARCHAR(50) NOT NULL UNIQUE, -- "business", "technical"
    strategic_weight DECIMAL(3,2) DEFAULT 1.0,  -- множитель для нормализации
    active          BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

-- Подкритерии (R, I, C, E)
CREATE TABLE rice_criteria (
    id              BIGSERIAL PRIMARY KEY,
    template_id     BIGINT NOT NULL REFERENCES rice_templates(id),
    parameter       VARCHAR(10) NOT NULL,        -- REACH, IMPACT, CONFIDENCE, EFFORT
    name            VARCHAR(300) NOT NULL,        -- "Тип фичи", "Кол-во пользователей"
    description     TEXT,                         -- подсказка
    selection_type  VARCHAR(20) NOT NULL,         -- SINGLE, MULTI
    sort_order      INTEGER NOT NULL DEFAULT 0,
    active          BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT NOW()
);

-- Варианты ответов для подкритериев
CREATE TABLE rice_criteria_options (
    id              BIGSERIAL PRIMARY KEY,
    criteria_id     BIGINT NOT NULL REFERENCES rice_criteria(id) ON DELETE CASCADE,
    label           VARCHAR(500) NOT NULL,        -- "< 100 пользователей"
    description     TEXT,                         -- подсказка
    score           DECIMAL(10,2) NOT NULL,       -- +1, +3, +5...
    sort_order      INTEGER NOT NULL DEFAULT 0
);

-- Оценки (привязка к issue)
CREATE TABLE rice_assessments (
    id              BIGSERIAL PRIMARY KEY,
    issue_key       VARCHAR(50) NOT NULL,         -- ключ Project или Epic в Jira
    template_id     BIGINT NOT NULL REFERENCES rice_templates(id),
    assessed_by     BIGINT REFERENCES users(id),  -- кто оценил
    confidence      DECIMAL(3,2),                 -- 0.4-1.0
    effort_manual   VARCHAR(10),                  -- S, M, L, XL (T-shirt)
    effort_auto     DECIMAL(10,2),                -- автоматический из оценки (person-months)
    total_reach     DECIMAL(10,2),                -- сумма баллов R
    total_impact    DECIMAL(10,2),                -- сумма баллов I
    effective_effort DECIMAL(10,2),               -- effort_auto ?? t-shirt value
    rice_score      DECIMAL(10,2),                -- (R × I × C) / E
    normalized_score DECIMAL(5,2),                -- 0-100 нормализованный
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW(),
    UNIQUE(issue_key)
);

-- Выбранные ответы
CREATE TABLE rice_assessment_answers (
    id              BIGSERIAL PRIMARY KEY,
    assessment_id   BIGINT NOT NULL REFERENCES rice_assessments(id) ON DELETE CASCADE,
    criteria_id     BIGINT NOT NULL REFERENCES rice_criteria(id),
    option_id       BIGINT NOT NULL REFERENCES rice_criteria_options(id),
    score           DECIMAL(10,2) NOT NULL        -- балл выбранного варианта
);

CREATE INDEX idx_rice_assessments_issue ON rice_assessments(issue_key);
CREATE INDEX idx_rice_answers_assessment ON rice_assessment_answers(assessment_id);
```

### Backend

```
com.leadboard.rice/
├── RiceTemplateEntity          — JPA: шаблон (Business/Technical)
├── RiceCriteriaEntity          — JPA: подкритерий
├── RiceCriteriaOptionEntity    — JPA: вариант ответа
├── RiceAssessmentEntity        — JPA: оценка
├── RiceAssessmentAnswerEntity  — JPA: выбранные ответы
├── RiceRepository              — Репозитории
├── RiceService                 — Бизнес-логика: расчёт, наследование, effort auto
├── RiceTemplateService         — CRUD шаблонов (Admin)
├── RiceAutoScoreIntegration    — Интеграция RICE → AutoScore boost
├── RiceController              — REST API
└── RiceConfig                  — Конфигурация (MAX_RICE_WEIGHT, etc.)
```

### API Endpoints

| Метод | Путь | Описание | Доступ |
|-------|------|----------|--------|
| **Шаблоны** |
| GET | `/api/rice/templates` | Список шаблонов | Все |
| GET | `/api/rice/templates/{id}` | Шаблон с подкритериями и вариантами | Все |
| POST | `/api/rice/templates` | Создать шаблон | Admin |
| PUT | `/api/rice/templates/{id}` | Обновить шаблон | Admin |
| **Оценки** |
| GET | `/api/rice/assessments/{issueKey}` | Получить оценку | Все |
| POST | `/api/rice/assessments` | Создать/обновить оценку | PM, PO, Admin |
| GET | `/api/rice/assessments/{issueKey}/effective` | Effective RICE (с учётом наследования) | Все |
| **Сводка** |
| GET | `/api/rice/ranking?templateId={id}` | Рейтинг по RICE Score | Все |

### Frontend

```
frontend/src/
├── components/
│   └── rice/
│       ├── RiceForm.tsx            — Форма оценки (подкритерии, чекбоксы/радио)
│       ├── RiceScoreBadge.tsx      — Бейдж с RICE Score на Board/Projects
│       ├── RiceSummary.tsx         — Развёрнутая сводка (breakdown по R, I, C, E)
│       └── RiceTemplateAdmin.tsx   — Админка: управление шаблонами
```

**RiceForm** — ключевой компонент:
```
┌─ RICE Оценка ── Шаблон: Business ─────────────────────────────────┐
│                                                                    │
│  REACH (охват)                                        Σ = 9       │
│  ┌──────────────────────────────────────────────────────┐         │
│  │  Тип фичи                               ○ +3  ○ +1  │         │
│  │  Кол-во пользователей        ○ +1  ○ +3  ○ +5  ○ +1/3K │     │
│  │  Кол-во клиентов             ○ +1  ○ +2  ○ +3  ○ +5  │         │
│  └──────────────────────────────────────────────────────┘         │
│                                                                    │
│  IMPACT (влияние)                                     Σ = 14      │
│  ┌──────────────────────────────────────────────────────┐         │
│  │  Инициатор запроса                       ○ +3  ○ +1  │         │
│  │  Тип задачи                  ☑ +5  ☐ +5  ☑ +1  ☐ +3  │        │
│  │  Соответствие целям                ☑ +1  ☑ +5         │         │
│  │  Экономия FTE           ○ 0  ○ +1  ○ +2  ○ +3  ...   │         │
│  │  ...                                                   │         │
│  └──────────────────────────────────────────────────────┘         │
│                                                                    │
│  CONFIDENCE (уверенность)                                         │
│  ○ High (1.0)  ○ Medium (0.8)  ○ Low (0.6)  ○ Very Low (0.4)    │
│                                                                    │
│  EFFORT (затраты)                                                 │
│  T-shirt: ○ S(1)  ● M(2)  ○ L(4)  ○ XL(8)                       │
│  ℹ После появления оценки Effort обновится автоматически          │
│                                                                    │
│  ═══════════════════════════════════════════════                   │
│  RICE Score = (9 × 14 × 0.8) / 2 = 50.4                         │
│  Normalized: 72/100                                                │
│                                                                    │
│                                          [Сохранить]  [Отмена]    │
└────────────────────────────────────────────────────────────────────┘
```

---

## Data Quality интеграция

Новые правила в DataQualityService:

```java
// Проект/эпик в PLANNING+ без RICE
if (isProjectOrStandaloneEpic(issue) && isPlanningOrLater(issue) && !hasRiceAssessment(issue)) {
    addAlert(WARNING, "RICE оценка отсутствует");
}
```

---

## План реализации (поэтапно)

### Этап 1: Шаблоны + CRUD
1. DB миграции (rice_templates, rice_criteria, rice_criteria_options)
2. Entities + Repositories
3. RiceTemplateService — CRUD шаблонов
4. Seed миграция с дефолтными шаблонами (Business + Technical)
5. API: шаблоны
6. Тесты

### Этап 2: Оценка + UI форма
1. DB миграции (rice_assessments, rice_assessment_answers)
2. RiceService — создание/обновление оценки, расчёт score
3. RiceController — API оценок
4. Роль PRODUCT_OWNER в AppRole
5. Frontend: RiceForm
6. Тесты

### Этап 3: Интеграция
1. Наследование RICE (проект → эпики)
2. Effort auto (подстановка из реальных оценок)
3. RiceAutoScoreIntegration — RICE boost в AutoScore
4. RiceScoreBadge на Board и Projects
5. Data Quality правила
6. Тесты

### Этап 4: Нормализация + рейтинг
1. Нормализация scores (0-100)
2. Strategic weight для шаблонов
3. API рейтинга
4. RiceTemplateAdmin (админка)
5. Тесты
