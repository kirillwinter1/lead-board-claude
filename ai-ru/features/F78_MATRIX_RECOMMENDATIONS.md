# F78. Рекомендации автопланера из матрицы — Фаза A

**Версия:** 0.78.0 | **Дата:** 2026-06-28 | **Дизайн:** docs/superpowers/specs/2026-06-28-matrix-autoplanner-recommendations-design.md

Read-only панель на экране Matrix под сеткой: **единый приоритизированный список**
triaged-стори (P1→P4). Стори показывается **один раз** — задача делается всем
конвейером SA→DEV→QA, поэтому роли это её **состав работ** (саб-таск роли + часы +
«Всего»), а не отдельные задачи (нельзя сделать QA без DEV). Стори без нарезки на
роли или с неоценёнными саб-тасками попадают в блок «требует нарезки/оценки».
Сверху — секция **Zero Bug Policy**: все открытые orphan-баги команды (всегда),
цель — довести до нуля.

> Изначально дизайн был «idle-роль → её часть задачи», но это неверно: роли —
> последовательный конвейер, а не независимые слоты. Модель переделана на
> per-story (см. историю коммитов).

## Изменение F77
Баги исключены из триажа матрицы (грид теперь Story/Task без багов,
`WorkflowConfigService.isBug()`). Баги — отдельная `board_category=BUG`, грузятся
для Zero Bug Policy через `MatrixService.loadOrphanBugs`, а не из STORY-набора.

## Backend (`com.leadboard.matrix`)
- `MatrixRecommendationService.getRecommendations(teamId)` — Zero Bug Policy
  (open orphan bugs) + triaged-стори (P1→P4), у каждой состав по ролям из её
  саб-тасков (`findByParentKeyIn`); неоценённые/ненарезанные → needsEstimation.
- `MatrixRecommendationController` → `GET /api/matrix/recommendations?teamId`
  (`@PreAuthorize canManageTeam`).
- DTO: `RecommendationViewDto { zeroBugPolicy, recommended, needsEstimation }`,
  `StoryRec { ..., roles: RoleSlice[], totalHours }`, `RoleSlice`, `ZeroBugPolicy`,
  `RecCard`.
- Reuse: `MatrixService.loadOrphans/loadOrphanBugs/isDone/isBug` (без дублирования).

## Frontend
- `components/matrix/MatrixRecommendations.tsx` под сеткой `MatrixPage`.
- `api/matrixApi.ts` → `getRecommendations`.

## НЕ в Фазе A
Timeline-интеграция с idle-окнами во времени (Фаза B), нарезка саб-тасков в Jira
(Фаза C), drag-перетаскивание последовательности на таймлайне (Фаза D),
пороги/алерты Zero Bug Policy, авто-назначение.
