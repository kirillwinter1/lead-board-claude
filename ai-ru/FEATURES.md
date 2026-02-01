# Список фич Lead Board

## Статус реализации

| # | Название | Статус | Дата | Спецификация |
|---|----------|--------|------|-------------|
| F1 | Bootstrap проекта | ✅ | 2026-01-22 | — |
| F2 | Jira Integration MVP | ✅ | 2026-01-22 | — |
| F3 | Jira Sync & Cache | ✅ | 2026-01-23 | — |
| F4 | OAuth 2.0 (Atlassian 3LO) | ✅ | 2026-01-23 | — |
| F5 | Team Management Backend | ✅ | 2026-01-23 | — |
| F6 | Team Management UI | ✅ | 2026-01-23 | — |
| F7 | Team Sync from Atlassian | ✅ | 2026-01-23 | — |
| F8 | Board v2 (Epic root) | ✅ | 2026-01-23 | — |
| F9 | Sub-task Estimates | ✅ | 2026-01-23 | — |
| F10 | Epic-Team Mapping | ✅ | 2026-01-23 | — |
| F11 | Rough Estimates для Epics | ✅ | 2026-01-23 | — |
| F13 | Автопланирование (AutoScore + Expected Done) | ✅ | 2026-01-24 | [features/F21](features/F21_UNIFIED_PLANNING.md) (заменил F13) |
| F14 | Timeline/Gantt | ✅ | 2026-01-24 | [features/F14](features/F14_TIMELINE.md) |
| F15 | WIP Limits (Stage 1) | ✅ | 2026-01-24 | [features/F15](features/F15_WIP_LIMITS.md) |
| F16 | Pipeline WIP + Stories | 📋 | — | [features/F16](features/F16_PIPELINE_WIP_STORIES.md) |
| F17 | Configurable Status Mapping | ✅ | 2026-01-24 | [features/F17](features/F17_STATUS_MAPPING.md) |
| F18 | Data Quality | ✅ | 2026-01-24 | [features/F18](features/F18_DATA_QUALITY.md) |
| F19 | Story AutoScore & Prioritization | ✅ | 2026-01-25 | [features/F19](features/F19_STORY_AUTOSCORE.md) |
| F20 | Story-Level Planning | ✅ | 2026-01-25 | [features/F20](features/F20_STORY_FORECAST.md) |
| F21 | Unified Planning Algorithm | ✅ | 2026-01-25 | [features/F21](features/F21_UNIFIED_PLANNING.md) |
| F22 | Team Metrics + DSR + Forecast Accuracy | ✅ | 2026-01-26 | [features/F22](features/F22_TEAM_METRICS.md) |
| F23 | Planning Poker | ✅ | 2026-01-28 | [features/F23](features/F23_PLANNING_POKER.md) |
| F24 | Team Metrics v2 (DSR Gauge, Forecast) | 🚧 | 2026-01-28 | [features/F22](features/F22_TEAM_METRICS.md) (объединён) |
| F25 | Manual Order + Recommendations | 🚧 | 2026-01-30 | [features/F25](features/F25_MANUAL_ORDER.md) |
| F26 | Employee Performance Dashboard | 📋 | — | — |
| F27 | RBAC | ✅ | 2026-02-01 | [features/F27](features/F27_RBAC.md) |
| F28 | RICE Scoring & AutoScore | 📋 | — | — |
| F29 | Project-Level Management | 💡 | — | — |

**Легенда:** ✅ Готово | 🚧 В работе | 📋 Planned | 💡 Idea

## Документация

- [ARCHITECTURE.md](ARCHITECTURE.md) — карта кодовой базы
- [RULES.md](RULES.md) — бизнес-правила и правила разработки
- [JIRA_WORKFLOWS.md](JIRA_WORKFLOWS.md) — Jira workflows (Epic, Story, Subtask)
- [API_PLANNING.md](API_PLANNING.md) — API документация
- [ROADMAP_V2.md](ROADMAP_V2.md) — роадмап F24-F29
- [TECH_DEBT.md](TECH_DEBT.md) — технический долг

## Диаграмма зависимостей

```
F1 → F2 → F3 → F8 → F9/F10 → F11 → F13 → F14/F15/F18
                 └→ F4 → F5 → F6/F7
F13 → F19 → F20 → F21
F22 → F24
```

## Технические исправления (changelog)

### 2026-01-30: F25 Manual Order + Recommendations
- Порядок эпиков/сторей через `manual_order` (drag & drop)
- `autoScore` теперь только для рекомендаций
- UI индикаторы: ↑ (выше по autoScore), ↓ (ниже), ● (соответствует)
- Удалён deprecated `manualPriorityBoost`
- Миграции: V21 (add manual_order), V22 (drop manual_priority_boost)

### 2026-01-28: Forecast Accuracy — рабочие дни, status changelog, estimate change
- Рабочие дни вместо календарных через WorkCalendarService
- Actual Start/End по StatusChangelog (fallback на startedAt/doneAt)
- Колонка "Оценка" (initial vs developing estimate)

### 2026-01-26: AutoScore refactoring — "Finish what's started"
- Status weight учитывает позицию в workflow (-5 to +30)
- Jira Priority вес увеличен до 20
- Нет оценки = штраф (-5)

### 2026-01-25: Board UX — tooltips, story ordering, expectedDone для stories
- Priority tooltips с breakdown (7 факторов epic, 9 факторов story)
- Stories сортируются по autoScore на Board и Timeline
- expectedDone для stories (remainingDays = ceil((estimate - spent) / 8h))

### 2026-01-23: Jira API cursor-based pagination
- `/rest/api/3/search/jql` перешёл на cursor-based пагинацию
- Добавлены `nextPageToken`, `isLast` в JiraSearchResponse
