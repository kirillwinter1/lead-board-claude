# TASK: Fix async SecurityContext propagation — ✅ NOT AN ISSUE

**Priority:** Critical
**Status:** ✅ NOT AN ISSUE (verified 2026-03-08)
**Review ID:** C3
**Files:**
- `backend/src/main/java/com/leadboard/auth/AuthorizationService.java`

## Проблема

`@Async` методы не имеют SecurityContext в потоке. `AuthorizationService.getCurrentAuth()` возвращает null → `getUserTeamIds()` возвращает пустой set → данные всех команд скрыты.

## Рекомендация

- Передавать account ID как параметр в async-методы вместо чтения из SecurityContext
- Или настроить `SecurityContextHolder.setStrategyName(MODE_INHERITABLETHREADLOCAL)` (но осторожно с thread pools)
- Проверить все `@Async` вызовы в проекте на использование AuthorizationService

## Результат верификации

Все методы AuthorizationService синхронные. @Async не используется в вызывающем коде. Проблема не воспроизводится — ложное срабатывание при ревью.
