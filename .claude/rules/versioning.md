# Versioning & Feature Numbering Rules

## Feature Numbers

- **F-numbers are assigned only to implemented features**, strictly sequential (F1, F2, ... F58, F59, ...)
- Numbers from ROADMAP_V2 and backlog are NOT reserved. Next number = last implemented + 1
- Implemented feature moves from `ai-ru/backlog/` to `ai-ru/features/` with new F-number
- In FEATURES.md: add to implemented table, mark in backlog as `Done -> F{N}`

## Version Bumping

- **ALWAYS bump version when implementing a feature.** Version in `backend/build.gradle.kts` and `frontend/package.json`.
- **Version = feature number:** F41 -> 0.41.0, F42 -> 0.42.0, etc.

## Feature Requirements

- Tests: JUnit5 for backend, cover main scenarios
- Run `./gradlew test` before commit, never commit with failing tests
- Update documentation in ai-ru/
- Commit documentation together with code
- **DO NOT mark feature as DONE until ALL plan steps are completed.** Always verify against the plan.
