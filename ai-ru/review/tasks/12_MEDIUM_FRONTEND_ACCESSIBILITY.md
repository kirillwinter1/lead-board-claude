# TASK: Fix accessibility issues in frontend

**Priority:** Medium
**Review IDs:** M24, M25, M26, M27, M32, M36
**Files:**
- `ProjectTimelinePage.tsx:615` — clickable div без role="button"/keyboard
- `QuarterlyPlanningPage.tsx:365,604` — clickable div/span без accessibility
- `ProjectsPage.tsx:334-344` — clickable div без keyboard handler
- `Modal.tsx:30` — backdrop div без role/keyboard
- `Layout.tsx:112` — avatar `alt=""` вместо `alt={displayName}`
- `TeamsPage.tsx:189-203` — color picker span без keyboard

## Рекомендация

- Заменить clickable `div` на `<button>` или добавить `role="button" tabIndex={0} onKeyDown={...}`
- Modal backdrop: добавить `role="presentation"`
- Avatar: `alt={auth.user.displayName}`
