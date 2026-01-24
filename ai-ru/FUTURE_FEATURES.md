# Будущие фичи (Backlog)

Этот документ содержит идеи и фичи для будущей реализации.

---

## F17. Подсветка зависших сторей

**Проблема:** Стори в статусе "In Progress" без активности (нет логов времени) — признак проблемы.

**Решение:**
- Отслеживать дату последнего лога времени в сторе
- Если > N дней без активности — подсветить жёлтым/красным
- Показать предупреждение в Insights: "Story PROJ-123 has no activity for 5 days"

**Критерии "зависания":**
- В статусе In Progress > 3 дней без логов → Warning (жёлтый)
- В статусе In Progress > 7 дней без логов → Critical (красный)

**UI:**
- Сегмент сторей с красной рамкой
- Tooltip: "No activity since Jan 15 (9 days ago)"
- Insight: "2 stories appear stalled - no time logged"

**Зависимости:** F16 (визуализация сторей)

---

## F18. Исторический лог работы (3 месяца назад)

**Проблема:** Нужно видеть как выглядел план в прошлом, сравнивать plan vs actual.

**Решение:**
- Хранить снапшоты прогноза (не только WIP, а полный forecast)
- Данные за прошлые даты не должны меняться
- UI для просмотра исторических данных

**Архитектура:**
```
forecast_snapshots
├── id
├── team_id
├── snapshot_date
├── forecast_json (полный ForecastResponse)
└── created_at
```

**Важно при проектировании F16:**
- Хранить историю сторей (статус на дату)
- Не перезаписывать старые данные при синхронизации

**UI:**
- Слайдер/календарь для выбора даты просмотра
- Сравнение "тогда vs сейчас"
- Подсветка изменений

---

## F19. Burndown/Burnup Chart

**Идея:** Классический agile chart для эпика или команды.

**Варианты:**
- Burndown по эпику (remaining work vs time)
- Burnup по команде (completed vs planned)
- Velocity chart (story points per sprint)

---

## F20. Dependency Management

**Идея:** Визуализация зависимостей между эпиками/сторями.

- Линии связей на Gantt
- Автоматический пересчёт дат при задержке зависимости
- Предупреждения о блокерах

---

## F21. Resource Allocation View

**Идея:** Видеть загрузку каждого члена команды.

```
John (DEV):  [Epic1][Epic2    ][Epic3]  80% loaded
Mary (QA):   [Epic1   ][Epic2]          60% loaded
Alex (SA):   [Epic1][Epic2][Epic3][E4]  100% loaded ⚠️
```

---

## F22. Sprint Planning Integration

**Идея:** Интеграция с Jira Sprints.

- Показать границы спринтов на Timeline
- Рекомендации по объёму работы в спринте
- Sprint Capacity vs Planned Work

---

## F23. Slack/Teams Notifications

**Идея:** Оповещения о важных событиях.

- Epic at risk (due date приближается)
- WIP exceeded
- Story stalled
- New epic added to queue

---

## F24. What-If Scenarios

**Идея:** Моделирование "что если".

- Что если добавить разработчика?
- Что если убрать эпик из scope?
- Что если сдвинуть due date?

Показать влияние на прогноз без изменения реальных данных.

---

## Приоритеты

| # | Фича | Ценность | Сложность | Приоритет |
|---|------|----------|-----------|-----------|
| F17 | Зависшие стори | High | Low | P1 |
| F18 | Исторический лог | High | Medium | P1 |
| F19 | Burndown | Medium | Medium | P2 |
| F20 | Dependencies | High | High | P2 |
| F21 | Resource View | Medium | Medium | P2 |
| F22 | Sprint Integration | Medium | Medium | P3 |
| F23 | Notifications | Low | Low | P3 |
| F24 | What-If | High | High | P3 |
