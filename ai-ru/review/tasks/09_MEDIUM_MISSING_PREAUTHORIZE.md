# TASK: Add @PreAuthorize to unprotected controllers

**Priority:** Medium
**Review IDs:** M13, M14
**Files:**
- `TeamMetricsController.java` — все endpoints без @PreAuthorize
- `PokerController.java` — мутирующие endpoints без @PreAuthorize

## Проблема

1. **TeamMetricsController:** Любой authenticated user (включая VIEWER) может читать метрики любой команды по произвольному teamId.
2. **PokerController:** Любой authenticated user может создавать poker sessions, добавлять стори, ставить оценки для любой команды.

## Рекомендация

- TeamMetricsController: добавить проверку `AuthorizationService.canAccessTeam(teamId)` на все endpoints
- PokerController: `@PreAuthorize` на мутирующие endpoints (createSession, addStory, revealVotes, setFinalEstimate)
