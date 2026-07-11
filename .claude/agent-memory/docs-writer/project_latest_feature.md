---
name: project-latest-feature
description: Tracks the current latest implemented feature number, version, and branch — always verify from files before quoting
metadata:
  type: project
---

Latest implemented feature: **F91** — UI Consistency Pass (frontend-only design audit: real visual bugs — MemberProfilePage status colors, TrendChart legend, landing AuditModal false-success on error; 6 new shared components RoleBadge/GradeBadge/ProgressBar/EmptyState/ColorPicker/DarkTooltip; RU→EN on remaining screens; palette fully on `constants/colors.ts` incl. new `TIMELINE_*` muted-Gantt tokens/GRADE_COLORS/ABSENCE_COLORS/WARNING_ORANGE; a11y aria-labels + inline errors instead of alert(); ~1180 lines dead/duplicated CSS removed, TimelinePage.css 1502→449)
Version: **0.91.0** (bumped 2026-07-11)
Spec: `ai-ru/features/F91_UI_CONSISTENCY.md`
Branch at time of docs: `feat/f91-ui-consistency`
Commit: docs commit for F91 bump (see `git log --oneline -3` on `feat/f91-ui-consistency` after this write)

Prior feature: **F90** — Log time (списание времени со страницы My Work: кнопка/модалка на строках задач, запись ворклога в Jira персональным OAuth-токеном пользователя, локальный апсерт; `POST /api/me/worklog` в `MyWorkController`, сервис `MyWorklogService`). Version 0.90.0. Spec: `ai-ru/features/F90_LOG_TIME_MY_WORK.md`.

Note: F89 (Planning Poker rework, PR #29) is now present in FEATURES.md but placed OUT OF NUMERIC ORDER — the table row order is F87 → F89 → F88 → F90 → F91 (F89 was added later by a different branch, between F88 and F90 rows, not resorted). When adding a new F-number row, append at the end of the table in whatever order the file currently has — do not try to "fix" the F89 ordering as part of an unrelated docs task.

**Why:** Needed to track latest F-number so future docs commits start from the right number.
**How to apply:** Always re-verify by reading `backend/build.gradle.kts`, `frontend/package.json`, and `ls ai-ru/features/` before quoting — do not trust this memory line alone. If two feature branches race for the same F-number, check FEATURES.md for an existing row before adding — don't overwrite a row that was already claimed by a parallel branch.

Related: [[feedback-verify-version-before-bump]]

## Backend test note

As of F90 (2026-07-08), full `./gradlew test` run: 1279 tests, 73 failed, 3 skipped, 1206 passed. All 73 failures re-verified (via JUnit XML parsing across all 14 failing test classes) to be `NoClassDefFoundError`/`ExceptionInInitializerError` from Testcontainers' `DockerClientProviderStrategy` — Docker not running locally, not a code regression. Same 14 integration/component classes as the F88 baseline (Sync/Team/Forecast/Metrics/Board/Poker Integration + Component tests). This count (~73) has been stable across multiple features — treat any deviation (new non-Testcontainers failure, or a shifted total) as a real regression to investigate, not more of the same baseline noise. Test count grew from 1264 (F88) to 1279 (+15, F90's own MyWorklogService/controller/security tests) — expect the total to keep climbing feature over feature while the ~73 Docker-failure count stays fixed.
