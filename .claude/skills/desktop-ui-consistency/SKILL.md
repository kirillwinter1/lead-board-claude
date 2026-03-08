---
name: desktop-ui-consistency
description: Desktop UI consistency review and refactoring guide. Use when fixing visual inconsistencies, refactoring inline styles to shared components, reviewing UI for design system violations, or standardizing pages visually. Desktop-only project.
argument-hint: "[page name, component, or 'audit' for full scan]"
allowed-tools: Read, Glob, Grep, Bash, Edit, Write, Agent
---

# Desktop UI Consistency — Review & Refactoring Guide

**Target:** $ARGUMENTS

Desktop-only project. No mobile/responsive concerns.

---

## Goal

Enforce visual consistency across all Lead Board pages by:
- Eliminating inline styles that duplicate shared patterns
- Routing ALL visual primitives through shared components
- Maintaining one color source of truth per semantic concept
- Keeping UI language uniform per page (no RU/EN mixing in app pages)

---

## Workflow

### Phase 1: Inspect Existing UI

Before changing anything, audit the target area.

**1.1 Identify which page(s) to inspect:**
```
Board → src/pages/BoardPage.tsx + BoardPage.css (1644 lines)
Timeline → src/pages/TimelinePage.tsx + TimelinePage.css (1480 lines)
Metrics → src/pages/TeamMetricsPage.tsx + TeamMetricsPage.css
Data Quality → src/pages/DataQualityPage.tsx + DataQualityPage.css
Projects → src/pages/ProjectsPage.tsx + ProjectsPage.css
Bugs → src/pages/BugMetricsPage.tsx + BugMetricsPage.css
Planning → src/pages/QuarterlyPlanningPage.tsx + QuarterlyPlanningPage.css
Settings → src/pages/SettingsPage.tsx + SettingsPage.css
Teams → src/pages/TeamsPage.tsx + TeamsPage.css
```

**1.2 Scan for inline styles:**
```bash
grep -n 'style={{' src/pages/<PageName>.tsx | wc -l
grep -n 'style={{' src/pages/<PageName>.tsx
```

**1.3 Scan for hardcoded colors:**
```bash
grep -nE '#[0-9a-fA-F]{3,6}' src/pages/<PageName>.tsx
```

**1.4 Check component imports — are shared components used?**
```bash
grep -E 'StatusBadge|TeamBadge|MetricCard|FilterBar|Modal|MultiSelectDropdown|SingleSelectDropdown|SearchInput|Skeleton|RiceScoreBadge' src/pages/<PageName>.tsx
```

### Phase 2: Find Reusable Primitives

**Shared components (MUST use, NEVER duplicate):**

| Component | File | Purpose | Imports |
|-----------|------|---------|---------|
| `StatusBadge` | `components/board/StatusBadge.tsx` | Render ANY status with correct color | 7 |
| `TeamBadge` | `components/TeamBadge.tsx` | Team name with accent color | 2 |
| `RiceScoreBadge` | `components/rice/RiceScoreBadge.tsx` | RICE score badge | 1 |
| `MetricCard` | `components/metrics/MetricCard.tsx` | Metric card with trend | 5 |
| `FilterBar` | `components/FilterBar.tsx` | Filter container + chips | 5 |
| `FilterChips` | `components/FilterChips.tsx` | Active filter chips | 5 |
| `MultiSelectDropdown` | `components/MultiSelectDropdown.tsx` | Multi-select filter | 3 |
| `SingleSelectDropdown` | `components/SingleSelectDropdown.tsx` | Single-select filter | 7 |
| `SearchInput` | `components/SearchInput.tsx` | Search with AI mode | 2 |
| `Modal` | `components/Modal.tsx` | Modal dialog | 8 |
| `Skeleton` | `components/Skeleton.tsx` | Loading placeholder | 6 |
| `ViewToggle` | `components/ViewToggle.tsx` | List/Gantt toggle | 1 |

**Shared contexts:**

| Context | Purpose | Key methods |
|---------|---------|-------------|
| `StatusStylesContext` | Status colors from DB | `useStatusStyles()` → StatusBadge |
| `WorkflowConfigContext` | Workflow config, icons, role colors | `getRoleColor(code)`, `getIssueTypeIconUrl(type)`, `isEpic()`, `isStory()`, `isBug()` |

**Shared helpers:**

| Helper | File | Purpose |
|--------|------|---------|
| `getIssueIcon(type, url)` | `components/board/helpers.ts` | Issue type icon (Jira → fallback) |
| `getIssueTypeIconUrl(type)` | `WorkflowConfigContext` | Jira icon URL by type |
| `getPriorityColor(priority)` | `helpers/priorityColors.ts` | Priority → hex color |

### Phase 3: Identify Duplication

**Known duplication hotspots (verified in codebase):**

#### 3.1 Status rendering without StatusBadge
- `DataQualityPage.tsx:150` — renders `{issue.status}` as plain text
- `landing/components/DemoBoard.tsx` — inline color style for status

**Fix:** Replace with `<StatusBadge status={issue.status} />`

#### 3.2 SummaryCard duplicates MetricCard
- `DataQualityPage.tsx:109-116` — inline `SummaryCard` function with borderLeftColor, padding, background
- Should use `MetricCard` or extract a shared `SummaryCard`

**Fix:** Replace with `<MetricCard title={...} value={...} />` or extend MetricCard

#### 3.3 SeverityBadge is page-local
- `DataQualityPage.tsx:94-102` — inline `SeverityBadge` with backgroundColor, color, border, padding, borderRadius, fontSize

**Fix:** Extract to `components/SeverityBadge.tsx` or parametrize StatusBadge

#### 3.4 DSR colors defined in 4+ files
- `DsrBreakdownChart.tsx:36-47` — `#36B37E`, `#FFAB00`, `#FF5630`
- `ForecastAccuracyChart.tsx:40-44` — same colors
- `AssigneeTable.tsx` — same colors inline
- `VelocityChart.tsx` — same colors

**Fix:** Centralize in a single constant file or extend `WorkflowConfigContext`

#### 3.5 Tooltip styling duplication
- `BoardPage.css` — `.priority-tooltip`, `.forecast-tooltip`, `.info-tooltip` (separate implementations)
- Similar tooltip patterns across MetricsPage

**Fix:** Extract base `.tooltip` class in App.css, page-specific variants extend it

#### 3.6 DataQualityPage doesn't use FilterBar
- Custom filter UI instead of shared `FilterBar` component

**Fix:** Migrate to `FilterBar` + `MultiSelectDropdown`

#### 3.7 Empty states not shared
- Each page implements its own "no data" message with different styling

**Fix:** Extract to `<EmptyState icon={...} message={...} />` component

### Phase 4: Refactor to Shared Layer

**Order of operations (least risk → most risk):**

1. **Replace plain-text status with StatusBadge** — zero-risk, visual improvement
2. **Replace page-local SummaryCard/SeverityBadge with shared components** — low risk
3. **Centralize DSR/Priority color constants** — extract to `constants/colors.ts`
4. **Migrate DataQualityPage filters to FilterBar** — medium risk, test thoroughly
5. **Extract shared EmptyState component** — low risk
6. **Consolidate tooltip CSS** — medium risk, verify positioning
7. **Convert TeamBadge/RiceScoreBadge inline styles to CSS** — low risk but many files

### Phase 5: Verify on Key Pages

After each change, visually verify these pages (desktop only):

```
/board           — Board (most complex, 1644 lines CSS)
/timeline        — Timeline/Gantt
/metrics         — Team Metrics (charts, MetricCards)
/data-quality    — Data Quality (tables, severity badges)
/projects        — Projects (list + Gantt toggle)
/bugs            — Bug Metrics
/planning        — Quarterly Planning
/settings        — Settings (workflow config)
/teams           — Teams
```

---

## Mandatory Rules

### R1: One status = one semantic color mapping across the entire app
- **Source of truth:** `StatusStylesContext` → `StatusBadge`
- **NEVER** render a status string without `StatusBadge`
- **NEVER** apply status colors manually (no `style={{ backgroundColor: statusColor }}`)

### R2: One type of button/filter/badge = one source of truth
- Status → `StatusBadge`
- Team → `TeamBadge` (with `team.color`)
- RICE → `RiceScoreBadge`
- Metric → `MetricCard`
- Filter → `FilterBar` + `MultiSelectDropdown` / `SingleSelectDropdown`
- Modal → `Modal`
- Loading → `Skeleton`
- Role color → `getRoleColor(code)` from `WorkflowConfigContext`
- Issue icon → `getIssueIcon(type, getIssueTypeIconUrl(type))`

### R3: No page-local visual primitives without strong justification
- Before creating a component in a page file, check `src/components/`
- If a similar component exists, extend it (add props/variants)
- If truly unique, create in `src/components/` — not inline in the page

### R4: Minimize inline styles for permanent UI patterns
- **OK to inline:** dynamic values (width from data, position from calculation)
- **NOT OK to inline:** colors, padding, fontSize, borderRadius, fontWeight for UI primitives
- Rule of thumb: if the same `style={{}}` appears 2+ times → extract to CSS class

### R5: No RU/EN mixing within one product screen
- App pages: English UI labels (exception: landing page is bilingual by design)
- Tooltips, hints, descriptions: same language as the page
- Data from Jira (task names, descriptions): displayed as-is (may be Russian)

### R6: Desktop consistency across pages
- Same spacing between header and content
- Same card shadow / border / border-radius
- Same font sizes for h2, h3, body text, labels
- Same table header styling
- Same filter bar height and spacing

---

## Anti-Patterns

| Anti-Pattern | Example in Codebase | Correct Approach |
|-------------|---------------------|------------------|
| Plain-text status rendering | `DataQualityPage.tsx:150` `{issue.status}` | `<StatusBadge status={issue.status} />` |
| Page-local badge function | `DataQualityPage.tsx:94` `SeverityBadge` inline | Extract to `components/SeverityBadge.tsx` |
| Page-local card function | `DataQualityPage.tsx:109` `SummaryCard` inline | Use `MetricCard` or extract shared component |
| DSR colors in 4+ files | `DsrBreakdownChart.tsx:36`, `AssigneeTable.tsx`, `ForecastAccuracyChart.tsx` | Single constant in `constants/colors.ts` |
| Inline badge styling | `TeamBadge.tsx` — entire badge in `style={{}}` | CSS class `.team-badge` |
| `color + '20'` opacity pattern | `FilterChips.tsx`, `ProjectGanttView.tsx` | CSS variable or `rgba()` helper |
| Custom filter UI | `DataQualityPage.tsx` — own filter implementation | Use `FilterBar` + `MultiSelectDropdown` |
| Per-page empty state | Every page has its own "no data" div | Shared `EmptyState` component |
| Tooltip style duplication | `.priority-tooltip`, `.forecast-tooltip`, `.info-tooltip` | Base `.tooltip` class + variants |

---

## Code Review Checklist

When reviewing a PR that touches UI:

- [ ] **StatusBadge used?** — Any status string MUST go through StatusBadge
- [ ] **TeamBadge used?** — Team name MUST use TeamBadge or team.color
- [ ] **No new inline styles for permanent patterns?** — colors, padding, fontSize in `style={{}}`
- [ ] **No hardcoded hex colors in TSX?** — Use constants, contexts, or CSS variables
- [ ] **No page-local component that duplicates shared one?** — Check `src/components/` first
- [ ] **FilterBar used for filters?** — Not custom divs with dropdowns
- [ ] **Role colors from getRoleColor()?** — Not hardcoded SA=#1558BC, DEV=#803FA5, QA=#206A83
- [ ] **Issue icons from getIssueIcon()?** — Not local icon imports
- [ ] **Language consistent?** — No RU labels in English page (except Jira data)
- [ ] **Empty state handled?** — Loading, error, and empty states present
- [ ] **Typography matches other pages?** — Same h2/h3 sizes, same label styles

---

## Smoke Test Checklist (after UI changes)

After any UI refactoring, verify visually:

- [ ] `/board` — Epic/story rows render, status badges colored, team badges show, filters work
- [ ] `/timeline` — Gantt bars show, role colors correct, project tooltips render
- [ ] `/metrics` — MetricCards display, charts render with correct colors, DSR breakdown works
- [ ] `/data-quality` — Severity badges colored, violations table expands, filters functional
- [ ] `/projects` — Project list loads, progress bars show, Gantt view renders
- [ ] `/bugs` — Bug metrics charts render, priority colors correct
- [ ] `/planning` — Quarterly cards display, capacity bars show
- [ ] `/settings/workflow` — Status color picker works, role configuration renders
- [ ] `/teams` — Team cards show with colors, member list renders

**Quick visual checks:**
- [ ] No `NaN`, `undefined`, or `null` visible as text
- [ ] No broken layout (overlapping elements, cut-off text)
- [ ] No missing colors (gray boxes where color should be)
- [ ] Filter selections persist when switching tabs
- [ ] Skeleton loaders show during data loading

---

## Implementation Order (recommended)

**Phase 1 — Quick wins (1-2 hours, zero risk):**
1. Replace `{issue.status}` in DataQualityPage with StatusBadge
2. Replace SummaryCard in DataQualityPage with MetricCard

**Phase 2 — Color centralization (2-3 hours, low risk):**
3. Extract DSR colors to `constants/colors.ts`
4. Update DsrBreakdownChart, ForecastAccuracyChart, AssigneeTable, VelocityChart to import from constants

**Phase 3 — Component extraction (3-4 hours, medium risk):**
5. Extract SeverityBadge to shared component
6. Extract EmptyState component
7. Migrate DataQualityPage filters to FilterBar

**Phase 4 — Style migration (4-6 hours, medium risk):**
8. Convert TeamBadge inline styles to CSS class
9. Convert RiceScoreBadge inline styles to CSS class
10. Consolidate tooltip base CSS

**Phase 5 — Language cleanup (2-3 hours, low risk):**
11. Standardize app page labels to English
12. Move Russian strings to constants (prep for i18n)

---

## Key File References

### Sources of Truth (extend, never duplicate)
| File | Role |
|------|------|
| `components/board/StatusBadge.tsx` | Status rendering |
| `components/board/StatusStylesContext.tsx` | Status colors from backend |
| `contexts/WorkflowConfigContext.tsx` | Workflow config, role colors, issue icons |
| `components/TeamBadge.tsx` | Team badge with color |
| `components/metrics/MetricCard.tsx` | Metric cards |
| `components/FilterBar.tsx` | Filter container |
| `components/FilterChips.tsx` | Filter chips |
| `components/Modal.tsx` | Modal dialogs |
| `components/Skeleton.tsx` | Loading skeletons |
| `helpers/priorityColors.ts` | Priority color mapping |
| `constants/teamColors.ts` | Team color palette |
| `components/board/helpers.ts` | Issue icons, formatCompact |
| `App.css` | Global styles, .btn classes, loading/error/empty |

### Worst Offenders (most inconsistency)
| File | Issue |
|------|-------|
| `pages/DataQualityPage.tsx` | Page-local SummaryCard, SeverityBadge, plain-text status, custom filters |
| `pages/ProjectsPage.tsx:47-69` | Inline ProgressBar, JiraLink, AssigneeBadge functions |
| `components/metrics/DsrBreakdownChart.tsx:36-47` | DSR color constants duplicated |
| `components/metrics/AssigneeTable.tsx` | DSR colors inline |
| `components/ProjectGanttView.tsx` | Heavy inline styles for progress/colors |
| `pages/BoardPage.css` | 1644 lines, contains shareable tooltip/badge patterns |

---

## Usage

**Full audit:**
```
/desktop-ui-consistency audit
```

**Single page review:**
```
/desktop-ui-consistency DataQualityPage
```

**After UI changes:**
Run the smoke test checklist above, or use `/qa <page>` for full visual testing with screenshots.
