# F13. Автопланирование (AutoScore + Expected Done)

## Статус

**Заменён фичей [F21 Unified Planning Algorithm](F21_UNIFIED_PLANNING.md).**

## Обзор

Первая версия автоматического планирования: расчёт приоритетов эпиков (AutoScore) и прогноз дат завершения (Expected Done) на основе capacity команды.

## Что было реализовано

### AutoScore
- Автоматический расчёт приоритета эпиков по нескольким факторам
- Факторы: статус в workflow, Jira Priority, Due Date, прогресс, размер, возраст
- Сортировка эпиков на доске по AutoScore

### Expected Done
- Прогноз даты завершения эпика
- Расчёт на основе оставшейся работы и capacity команды по ролям
- Pipeline SA → DEV → QA

## Эволюция

F13 стал основой для более продвинутых фич:
- **F19** — Story AutoScore (приоритизация на уровне stories)
- **F20** — Story-Level Planning (прогноз по stories с учётом assignee)
- **F21** — Unified Planning Algorithm (объединение всех алгоритмов в один)

Подробная спецификация текущей реализации: [F21_UNIFIED_PLANNING.md](F21_UNIFIED_PLANNING.md)
