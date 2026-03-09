# F66: Jira Priority Icons on Board

**Версия:** 0.66.0
**Дата:** 2026-03-09

## Описание

Отображение иконок приоритета из Jira на борде, рядом с иконкой типа задачи (перед ключом).

## Реализация

### Backend
- `BoardNode.priority` — новое поле, маппится из `JiraIssueEntity.priority`
- `GET /api/admin/jira-metadata/priorities` — новый endpoint, кэш 60 мин
  - Jira API: `GET /rest/api/3/priority` → `id`, `name`, `iconUrl`

### Frontend
- `WorkflowConfigContext` — загружает приоритеты в `priorityIcons` map
- `getPriorityIconUrl(name)` — хелпер для получения URL иконки
- `BoardRow` — отображает `<img>` с иконкой приоритета между issue type icon и ключом
- CSS: `.priority-icon` — 16x16px, flex-shrink: 0

## Паттерн

Аналогичен issue type icons (F29): Jira API → JiraMetadataService → endpoint → WorkflowConfigContext → BoardRow.
