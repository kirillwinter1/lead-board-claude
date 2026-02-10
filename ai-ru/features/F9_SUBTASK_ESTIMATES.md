# F9. Sub-task Estimates

## Обзор

Оценки и time logging хранятся исключительно на уровне подзадач. Агрегация снизу вверх: Subtask → Story → Epic.

## Поля оценки (JiraIssueEntity)

| Поле | Описание |
|------|----------|
| `originalEstimateSeconds` | Исходная оценка из Jira |
| `remainingEstimateSeconds` | Оставшееся время (Jira) |
| `timeSpentSeconds` | Залогированное время |

## Effective Estimate

```java
if (remainingEstimateSeconds != null)
    return timeSpentSeconds + remainingEstimateSeconds;
else
    return originalEstimateSeconds;
```

Приоритет `remaining + spent` над `original` — учитывает переоценки в процессе работы.

## Расчёт прогресса

```
progress = (loggedSeconds / estimateSeconds) * 100%
```

### Агрегация по ролям

Подзадачи категоризируются по типу:
- **Аналитика** → SA
- **Разработка** → DEV
- **Тестирование** → QA

Для каждой роли суммируются `estimateSeconds` и `loggedSeconds` всех подзадач.

### Агрегация вверх

```
Story estimate = sum(subtask estimates) по ролям
Epic estimate = sum(story estimates) по ролям
```

## Отображение (Frontend)

- Прогресс-бар с процентом
- Оставшиеся дни: `(estimate - logged) / 8h`
- Формат: `logged → estimate` (например, "3д → 10д")
- 1 человеко-день = 8 часов

## Ключевое правило

**Story БЕЗ подзадач с оценками НЕ может быть запланирована.** Все оценки и time logging — только на уровне Subtask.
