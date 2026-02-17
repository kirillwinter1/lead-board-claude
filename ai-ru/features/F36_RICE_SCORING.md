# F36: RICE Scoring

**Дата:** 2026-02-17
**Статус:** Реализована
**Бэклог:** BF4 RICE Scoring

## Описание

Конфигурируемая система оценки бизнес-ценности проектов и эпиков по методологии RICE. Шаблонный движок с настраиваемыми подкритериями, автоматический расчёт Effort из subtask'ов, интеграция с AutoScore и Data Quality.

**Формула:** `RICE Score = (Reach × Impact × Confidence) / Effort`

## Изменения

### Backend

#### База данных

**V34 — rice_templates, rice_criteria, rice_criteria_options:**
- `rice_templates` — шаблоны (Business, Technical) с strategic weight
- `rice_criteria` — подкритерии (parameter: REACH/IMPACT/CONFIDENCE/EFFORT, selection_type: SINGLE/MULTI)
- `rice_criteria_options` — варианты ответов с баллами

**V35 — seed data:**
- Шаблон Business: 3 подкритерия Reach + 7 подкритериев Impact
- Шаблон Technical: 2 подкритерия Reach + 5 подкритериев Impact
- Confidence и Effort — стандартные для обоих шаблонов

**V36 — rice_assessments, rice_assessment_answers:**
- `rice_assessments` — оценки по issueKey (UNIQUE), хранит total_reach, total_impact, confidence, effective_effort, rice_score, normalized_score
- `rice_assessment_answers` — выбранные ответы (criteria_id + option_id + score)

#### Пакет `com.leadboard.rice`

| Класс | Назначение |
|-------|-----------|
| `RiceTemplateEntity` | JPA: шаблон с strategic_weight |
| `RiceCriteriaEntity` | JPA: подкритерий (parameter, selection_type, sort_order) |
| `RiceCriteriaOptionEntity` | JPA: вариант ответа (label, score) |
| `RiceAssessmentEntity` | JPA: оценка (issue_key, template_id, scores, normalized) |
| `RiceAssessmentAnswerEntity` | JPA: выбранный ответ |
| `RiceTemplateService` | CRUD шаблонов, расчёт min/max score range для нормализации |
| `RiceAssessmentService` | Создание/обновление оценок, расчёт RICE Score, effort auto, нормализация, batch-загрузка |
| `RiceController` | REST API (8 endpoints) |

#### RiceAssessmentService — ключевая логика

- **Расчёт:** `(totalReach × totalImpact × confidence) / effectiveEffort`
- **Нормализация:** `(rawScore - minPossible) / (maxPossible - minPossible) × 100 × strategicWeight`
- **Effort auto:** агрегация из subtask estimates → person-months (`totalHours / 160`)
  - Project: суммирует через Epic → Story → Subtask
  - Epic: суммирует через Story → Subtask
- **Effort priority:** auto estimate > T-shirt answers > manual T-shirt
- **Batch:** `getAssessments(Collection<String> issueKeys)` для эффективной загрузки

#### AutoScore интеграция

- Новый фактор `riceBoost` в `AutoScoreCalculator` (вес: 15 баллов)
- **Наследование:** эпик внутри проекта использует RICE Score проекта
- **Standalone:** эпик без проекта использует собственный RICE Score
- **Preloading:** `preloadRiceData()` загружает все RICE разом перед расчётом
- Без RICE → boost = 0 (нейтрально, не штрафуется)

#### Data Quality интеграция

- Правило `RICE_MISSING` (WARNING): проект/эпик в статусе PLANNING+ без RICE-оценки

#### ProjectService интеграция

- `listProjects()` загружает RICE через `RiceAssessmentService.getAssessments()`
- `ProjectDto` содержит `riceScore` и `riceNormalizedScore`

### API Endpoints

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/api/rice/templates` | Список шаблонов |
| GET | `/api/rice/templates/{id}` | Шаблон с критериями и опциями |
| GET | `/api/rice/templates/by-code/{code}` | Шаблон по коду |
| POST | `/api/rice/templates` | Создать шаблон |
| PUT | `/api/rice/templates/{id}` | Обновить шаблон |
| GET | `/api/rice/templates/{id}/score-range` | Min/max диапазон для нормализации |
| GET | `/api/rice/assessments/{issueKey}` | Получить оценку |
| POST | `/api/rice/assessments` | Создать/обновить оценку |
| GET | `/api/rice/ranking` | Рейтинг по normalized score (фильтр ?templateId=) |

### Frontend

#### Компоненты

| Компонент | Назначение |
|-----------|-----------|
| `RiceForm.tsx` | Универсальная форма оценки (для любого issueKey). Группы REACH/IMPACT/CONFIDENCE/EFFORT, радио/чекбоксы, live preview score |
| `RiceScoreBadge.tsx` | Бейдж с RICE Score + normalized. Цветовая кодировка: зелёный ≥70, оранжевый ≥40, серый <40 |

#### API клиент

`frontend/src/api/rice.ts` — интерфейсы и axios-вызовы для всех RICE endpoints.

#### Интеграция

- `ProjectsPage.tsx`: RiceForm в развёрнутом виде проекта, RiceScoreBadge на карточках
- Единая RICE-форма: работает и для проектов, и для эпиков (по issueKey)

### Тесты

| Тест | Покрытие |
|------|----------|
| `RiceTemplateServiceTest` | CRUD шаблонов, score range расчёт |
| `RiceAssessmentServiceTest` | Формула RICE, нормализация, effort auto, assessment CRUD |
| `AutoScoreCalculatorTest` | RICE boost, наследование project→epic, standalone epic |
| `ProjectServiceTest` | RICE поля в ProjectDto |
| `DataQualityServiceTest` | Правило RICE_MISSING |

## Архитектурные решения

### Единая таблица вместо project_overlays

Спека BF5 предполагала отдельную таблицу `project_overlays` с простыми полями (reach=integer, impact=enum). Вместо этого реализована единая `rice_assessments` для всех типов задач — гибче и без дублирования.

### Шаблонный движок

Вместо фиксированных полей — настраиваемые шаблоны с подкритериями. Позволяет бизнесу и техническим задачам оцениваться по разным критериям, но сравниваться через нормализацию.

### Effort auto-compute

Effort автоматически рассчитывается из реальных оценок subtask'ов (когда доступны), что устраняет необходимость ручного ввода на поздних стадиях проекта.
