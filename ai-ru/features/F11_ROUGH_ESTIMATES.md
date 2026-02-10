# F11. Rough Estimates для Epics

## Обзор

Грубые оценки трудозатрат по ролям для эпиков на ранних стадиях (до декомпозиции на stories/subtasks). Локальные данные Lead Board, не синхронизируются обратно в Jira.

## Концепция

Эпик в статусе TODO (Новое, Requirements, Rough Estimate, Запланировано) ещё не декомпозирован. Rough estimates позволяют оценить объём работы для планирования до появления подзадач.

Когда эпик переходит в работу (Developing+) и появляются подзадачи с оценками — rough estimates заменяются агрегацией из дочерних задач.

## Поля (JiraIssueEntity)

| Поле | Тип | Описание |
|------|-----|----------|
| `rough_estimate_sa_days` | DECIMAL(10,1) | Оценка для SA (дни) |
| `rough_estimate_dev_days` | DECIMAL(10,1) | Оценка для DEV (дни) |
| `rough_estimate_qa_days` | DECIMAL(10,1) | Оценка для QA (дни) |
| `rough_estimate_updated_at` | TIMESTAMP | Когда обновлено |
| `rough_estimate_updated_by` | VARCHAR | Кем обновлено |

## Конвертация

```
estimate_seconds = days × 8 часов × 3600
```

## API

| Метод | Путь | Описание |
|-------|------|----------|
| PATCH | `/api/epics/{key}/rough-estimate/{role}` | Обновить оценку по роли |
| GET | `/api/epics/config/rough-estimate` | Конфигурация (допустимые статусы, min/max, step) |

## Frontend

- **Epic в TODO:** редактируемые чипы по ролям (клик → ввод значения)
- **Epic в работе:** чипы показывают агрегацию из подзадач, rough estimates как справочные

## Конфигурация (RoughEstimateProperties)

- Допустимые статусы для редактирования
- Минимальное/максимальное значение в днях
- Шаг ввода

## Защита при синхронизации

Rough estimates — локальные поля. При Jira sync они сохраняются во временные переменные и восстанавливаются после обновления (см. F3).

## Использование в планировании

- `UnifiedPlanningService` использует rough estimates как fallback, когда нет дочерних задач с оценками
- `AutoScoreCalculator` учитывает наличие/отсутствие оценок в факторе Size

## Миграции

- `V6__add_rough_estimate.sql` — базовое поле rough_estimate
- `V7__rough_estimate_by_role.sql` — разделение на SA/DEV/QA
