# QA Report: F61 Projects UX Polish
**Date:** 2026-03-06
**Tester:** Claude QA Agent
**Scope:** ProjectsPage, ProjectTimelinePage, ViewToggle, Layout, DsrBreakdownChart, ForecastAccuracyChart

## Summary
- Overall status: **PASS WITH ISSUES**
- Unit tests: N/A (no frontend tests for these pages)
- Frontend build: PASS (0 errors, 0 warnings)
- API tests: Projects API returns 500 (pre-existing backend issue, NOT related to F61)
- Visual tests: 3 screenshots taken, all pages render correctly

## Verified F61 Changes

### Bug Fix: Double `/browse/browse/` in Jira URLs
- **VERIFIED** on ProjectsPage: `LB-294` links to `https://kirillwinter.atlassian.net/browse/LB-294` (correct)
- **VERIFIED** on ProjectTimelinePage: `LB-202` links to `https://kirillwinter.atlassian.net/browse/LB-202` (correct)
- **VERIFIED** in DsrBreakdownChart: removed extra `/` before epicKey
- **VERIFIED** in ForecastAccuracyChart: removed `/browse/` prefix (jiraBaseUrl already includes it)

### UX: ViewToggle (List | Gantt)
- **VERIFIED**: Segmented control renders in both pages
- **VERIFIED**: "List" highlighted on `/projects`, "Gantt" highlighted on `/project-timeline`
- **VERIFIED**: Navigation between views works correctly
- **VERIFIED**: "Project Timeline" removed from navbar, Projects tab active on both routes

### UX: Sorting on ProjectsPage
- **VERIFIED**: SingleSelectDropdown with 6 sort options renders
- Default, Progress asc/desc, RICE Score desc, Expected Done, Epics count desc

### UX: Team filter on ProjectTimelinePage
- **VERIFIED**: MultiSelectDropdown with team names and color dots
- **VERIFIED**: Filtering by team correctly filters epics within projects

### UX: English labels on ProjectTimelinePage
- **VERIFIED**: All Russian labels replaced:
  - "Масштаб" -> removed (just "Zoom" dropdown)
  - "День/Неделя/Месяц" -> "Day/Week/Month"
  - "Свернуть все" -> "Collapse all"
  - "Развернуть все" -> "Expand all"
  - "Сегодня" -> "Today"
  - "Rough est." -> "Forecast"
  - "Нед" -> "W" (week headers)
  - "ч" -> "h" (hours)
  - "Прогресс" -> "Progress"
  - Date formats: ru-RU -> en-US

### Design System Compliance
- **VERIFIED**: StatusBadge used for all status rendering (epic labels + tooltip)
- **VERIFIED**: TeamBadge used for team names (replaces manual styled spans)
- **VERIFIED**: Removed `getContrastColor()` and `getStatusColorFromStyles()` (unused after StatusBadge)
- **VERIFIED**: Removed unused CSS classes `.pt-epic-team`, `.pt-epic-status`, `.pt-role-line`

## Bugs Found

### Medium Priority

**M1: `error.message` exposed in ProjectTimelinePage**
- File: `ProjectTimelinePage.tsx:324`
- `setError('Failed to load timeline: ' + err.message)` — may expose server details
- Pre-existing issue (documented in tech-debt.md as L7)

**M2: Scroll-sync effect has `[projects]` dependency**
- File: `ProjectTimelinePage.tsx:340`
- `useEffect(..., [projects])` — listener torn down/re-attached on every filter change
- Should be `[]` since logic only depends on stable refs
- Pre-existing (documented as M5 in tech-debt.md)

**M3: Animation delay scales indefinitely**
- File: `ProjectTimelinePage.tsx:795,811`
- `animationDelay: ${rowIndex * 0.05}s` — 100th row waits 5s
- Should cap: `Math.min(rowIndex * 0.05, 0.5)`
- Pre-existing (documented as M6 in tech-debt.md)

### Low Priority

**L1: ViewToggle uses inline styles only**
- File: `ViewToggle.tsx`
- New shared component with hardcoded colors. Consider CSS class for theming.
- Pre-existing (documented as L3 in tech-debt.md)

**L2: Clickable project card div without keyboard accessibility**
- File: `ProjectsPage.tsx:401` — `<div onClick={...}>` without role="button", tabIndex, onKeyDown
- File: `ProjectTimelinePage.tsx:661` — `.pt-project-label` same issue
- Pre-existing (documented as M4 in tech-debt.md)

## Visual Review

### Projects List View (`f61_qa_projects_list.png`)
- Layout: Clean, well-structured cards with proper spacing
- ViewToggle: Positioned correctly next to page title
- Filters: Search, PM, Status, Sort — all render as pill-shaped dropdowns
- StatusBadge: Renders with correct colors ("HOBOE" in grey, "DEVELOPING" in blue)
- ProgressBar: Correct widths and colors
- AssigneeBadge: Shows avatar and name
- Jira links: Blue, clickable, correct URLs

### Projects Expanded View (`f61_qa_projects_expanded.png`)
- Detail table: Properly formatted with headers
- TeamBadge: Color-coded team badges
- StatusBadge: Consistent rendering in table cells
- AlignmentBadge: Green checkmarks for on-track epics
- RICE recommendation: Yellow info banner visible
- RICE Scoring button: Positioned bottom-right

### Gantt View (`f61_qa_projects_gantt.png`)
- ViewToggle: "Gantt" highlighted
- Legend: SA/DEV/QA with correct phase colors + Today + Forecast
- TeamBadge: Color-coded in epic labels
- StatusBadge: Rendered in epic label rows
- Gantt bars: Phase-colored segments visible
- Bar text: Role remaining days (e.g., "DEV:3/QA:4d")
- Today line: Blue vertical line at correct position
- Week zoom: Headers show "Mar 2", "Mar 9" etc. (English)
- Collapse all button styled correctly

## Test Coverage Gaps
- No frontend unit tests for ProjectsPage or ProjectTimelinePage
- No tests for ViewToggle component
- No integration tests for Jira link generation

## Recommendations
- Fix pre-existing tech debt items (see tech-debt.md)
- Add keyboard accessibility to clickable card divs
- Consider adding snapshot tests for ViewToggle
