# F78. Рекомендации автопланера из матрицы — Фаза A

**Версия:** 0.78.0 | **Дата:** 2026-06-28 | **Дизайн:** docs/superpowers/specs/2026-06-28-matrix-autoplanner-recommendations-design.md

Read-only панель на экране Matrix под сеткой: для каждой недогруженной роли —
triaged-стори (P1→P4), которые можно взять, сматченные на уровне саб-таска роли
(`workflow_role` + оценка), и отдельно те, что требуют нарезки/оценки. Сверху —
секция **Zero Bug Policy**: все открытые orphan-баги команды (всегда, независимо
от простоя), цель — довести до нуля.

## Изменение F77
Баги исключены из триажа матрицы (грид теперь Story/Task без багов,
`WorkflowConfigService.isBug()`). Баги участвуют только в рекомендациях.

## Backend (`com.leadboard.matrix`)
- `MatrixRecommendationService.getRecommendations(teamId)` — Zero Bug Policy
  (open orphan bugs) + idle-роли (`RoleLoadService`, `status==IDLE`) ×
  triaged-стори, сматченные по саб-таскам ролей (`findByParentKeyIn`).
- `MatrixRecommendationController` → `GET /api/matrix/recommendations?teamId`
  (`@PreAuthorize canManageTeam`).
- DTO: `RecommendationViewDto { zeroBugPolicy, roles }`, `ZeroBugPolicy`,
  `RoleRecommendation`, `RecCard`.
- Reuse: `MatrixService.loadOrphans/isDone/isBug` (без дублирования правил).

## Frontend
- `components/matrix/MatrixRecommendations.tsx` под сеткой `MatrixPage`.
- `api/matrixApi.ts` → `getRecommendations`.

## НЕ в Фазе A
Timeline-интеграция с idle-окнами во времени (Фаза B), нарезка саб-тасков в Jira
(Фаза C), drag-перетаскивание последовательности на таймлайне (Фаза D),
пороги/алерты Zero Bug Policy, авто-назначение.
