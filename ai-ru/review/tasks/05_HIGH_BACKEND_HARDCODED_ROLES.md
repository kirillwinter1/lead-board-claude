# TASK: Remove hardcoded role/type/status fallbacks in backend

**Priority:** High
**Review IDs:** H1, H2, H3, H4, H5, H6, M1, M3
**Files:**
- `WorkflowConfigService.java:335` — `getDefaultRoleCode()` → `.orElse("DEV")`
- `WorkflowConfigService.java:453-463` — `determinePhase()` → hardcoded "SA", "QA"
- `WorkflowConfigService.java:663-664` — `getFirstStatusNameForCategory()` → "Done", "In Progress"
- `WorkflowConfigService.java:744` — `getStoryTypeName()` → `.orElse("Story")`
- `TeamMemberEntity.java:26` — `private String role = "DEV"`
- `PlanningConfigDto.java:57-81` — `defaults()` hardcodes "SA", "DEV", "QA"
- `SimulationPlanner.java:340` — `.orElse("Epic")`
- `PokerController.java:36-41` — hardcoded `ELIGIBLE_STATUSES`

## Проблема

Эти fallback-значения нарушают правило проекта: ЗАПРЕЩЕНО хардкодить роли, типы задач, статусы. Для тенантов с нестандартной конфигурацией (не SA/DEV/QA) эти fallback'и приводят к некорректному поведению.

## Рекомендация

- **`getDefaultRoleCode()`** → throw `IllegalStateException` или вернуть первую роль из pipeline order
- **`determinePhase()`** → убрать substring fallbacks, использовать `getDefaultRoleCode()` + log warning
- **`getFirstStatusNameForCategory()`** → вернуть null, пусть caller обработает
- **`getStoryTypeName()`** → вернуть null или throw
- **`TeamMemberEntity.role`** → убрать field default, получать из WorkflowConfigService при создании
- **`PlanningConfigDto.defaults()`** → убрать no-arg version, требовать передачу roleCodes
- **`SimulationPlanner`** → использовать `getEpicTypeNames().getFirst()`
- **`PokerController`** → использовать `getStatusNamesByCategory()`
