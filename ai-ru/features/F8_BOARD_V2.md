# F8. Board v2 (Epic Root)

## Обзор

Основная доска приложения с трёхуровневой иерархией Epic → Story/Bug → Subtask, агрегацией данных и drag & drop.

## Иерархия

```
Epic (корневой узел, фильтр по команде)
├── Story / Bug (дочерние, связь через parentKey)
│   ├── Subtask — Аналитика (SA)
│   ├── Subtask — Разработка (DEV)
│   └── Subtask — Тестирование (QA)
```

## Агрегация данных (BoardService)

1. Загрузка задач по фильтрам (query, statuses, teamIds)
2. Разделение по типам: эпики, stories, subtasks
3. Построение дерева через parent-child связи
4. Каждый узел → `BoardNode` DTO:
   - Estimates, logged time, прогресс
   - Роль-специфичные данные (SA/DEV/QA breakdown)
   - Team info, alerts, autoScore

## Сортировка

- **Эпики и stories:** по `manualOrder` (ascending), fallback на `autoScore` (descending)
- Задачи с manualOrder идут первыми
- Drag & drop через dnd-kit для переупорядочивания

## Frontend (BoardPage.tsx)

- Разворачиваемые строки (Epic → Stories → Subtasks)
- Фильтры: поиск, статусы, команды
- Цветовые бейджи по ролям (SA синий, DEV зелёный, QA оранжевый)
- Progress bar с процентом выполнения
- Priority tooltips с breakdown по факторам
- Alerts для проблемных задач

## API

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/api/board` | Данные доски с фильтрами |
| GET | `/api/board/summary` | Сводка по доске |
