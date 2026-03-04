# TASK: Fix async SecurityContext propagation

**Priority:** Critical
**Review ID:** C3
**Files:**
- `backend/src/main/java/com/leadboard/auth/AuthorizationService.java`
- All `@Async` methods that call AuthorizationService

## Проблема

`@Async` методы не имеют SecurityContext в потоке. `AuthorizationService.getCurrentAuth()` возвращает null → `getUserTeamIds()` возвращает пустой set → данные всех команд скрыты.

## Рекомендация

- Передавать account ID как параметр в async-методы вместо чтения из SecurityContext
- Или настроить `SecurityContextHolder.setStrategyName(MODE_INHERITABLETHREADLOCAL)` (но осторожно с thread pools)
- Проверить все `@Async` вызовы в проекте на использование AuthorizationService
