---
name: project-latest-feature
description: Tracks the current latest implemented feature number, version, and branch — always verify from files before quoting
metadata:
  type: project
---

Latest implemented feature: **F78** — Рекомендации автопланера из матрицы (Фаза A)
Version: **0.78.0** (bumped 2026-06-28)
Spec: `ai-ru/features/F78_MATRIX_RECOMMENDATIONS.md`
Branch at time of docs: `feat/f78-matrix-recommendations`

**Why:** Needed to track latest F-number so future docs commits start from the right number.
**How to apply:** Always re-verify by reading `backend/build.gradle.kts`, `frontend/package.json`, and `ls ai-ru/features/` before quoting — do not trust this memory line alone.

Related: [[feedback-verify-version-before-bump]]

## Backend test note
73 integration/component tests fail with H2 (in-memory DB) due to PostgreSQL-specific types (`TEXT[]`, `jsonb`). These are **pre-existing** failures unrelated to docs changes — confirmed by running tests on both original and docs-only branch. Pure-unit tests (AutoScore, DataQuality, etc.) all pass.
