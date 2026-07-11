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
Board → src/pages/BoardPage.tsx + BoardPage.css (~1460 lines)
Timeline → src/pages/TimelinePage.tsx + TimelinePage.css (~450 lines, down from ~1500 after F91 CSS hygiene)
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
| `StatusBadge` | `components/board/StatusBadge.tsx` | Render ANY status with correct color; `maxWidth` prop truncates long statuses | 7+ |
| `TeamBadge` | `components/TeamBadge.tsx` | Team name with accent color | 2 |
| `RiceScoreBadge` | `components/rice/RiceScoreBadge.tsx` | RICE score badge | 1 |
| `SeverityBadge` | `components/SeverityBadge.tsx` | ERROR/WARNING/INFO badge, re-exports `SEVERITY_COLORS` | 3+ |
| `RoleBadge` | `components/RoleBadge.tsx` (F91) | Member role pill (SA/DEV/QA + custom) — color from `getRoleColor()`, never hardcoded | — |
| `GradeBadge` | `components/GradeBadge.tsx` (F91) | Member seniority pill (junior/middle/senior) — colors from `GRADE_COLORS` | — |
| `ProgressBar` | `components/ProgressBar.tsx` (F91) | Single-segment progress bar with ARIA `role="progressbar"`. **Not** for stacked/multi-segment bars — `CapacityBars` and `ProjectGanttView` stay custom by design (see JSDoc) | — |
| `EmptyState` | `components/EmptyState.tsx` (F91) | Unified "no data" placeholder, `variant="page"\|"inline"` | — |
| `ColorPicker` | `components/ColorPicker.tsx` (F91) | Popover color picker with fixed-position dropdown; consolidated 3 prior copies (TeamsPage, WorkflowConfigPage×2) | — |
| `DarkTooltip` | `components/DarkTooltip.tsx` (F91) | Portal-rendered dark tooltip for Timeline (`Title`/`Label`/`Value`/`Divider`/`Progress` subcomponents). **Not** for Board's light hover-cards (`HoverInfoCard`/`IssueTooltip`/`ProjectTooltip`/`StatusHistoryTooltip`) or CSS-anchored arrow tooltips (`MyWorklogCalendar`, `AbsenceTimeline`) — those are deliberately separate patterns | — |
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

**Status as of F91 (2026-07-11):** the historical hotspots below (status
rendering, SeverityBadge, DSR colors, tooltips, FilterBar, empty states) were
closed by the 2026-03-08 Desktop UI Consistency pass and the F91 UI
Consistency Pass. `DataQualityPage` already uses `StatusBadge` + `FilterBar`;
`SeverityBadge`, DSR colors, tooltips, empty states and color pickers are now
shared components/tokens (see the component table above and
`constants/colors.ts`). Treat this section as a checklist to re-verify, not
a fixed list of known-bad files — new pages/components can reintroduce the
same patterns.

**Current known hotspots (verified in codebase, F91):**

#### 3.1 `DataQualityPage.tsx` `SummaryCard` still takes a raw hex `color` prop
- `pages/DataQualityPage.tsx:67` — local `SummaryCard` function, called with
  literal hex colors (`"#6b7280"`, `"#dc2626"`, `"#d97706"`, `"#9ca3af"`)
  instead of `SEVERITY_COLORS`/`constants/colors.ts` tokens.

**Fix:** Pass tokens from `constants/colors.ts` (or `SEVERITY_COLORS`) instead
of hardcoded hex; consider whether `SummaryCard` can become `MetricCard`.

#### 3.2 `ProjectsPage.tsx` still has page-local `JiraLink`/`AssigneeBadge`
- `pages/ProjectsPage.tsx:59` (`JiraLink`), `:78` (`AssigneeBadge`) — small
  page-local components, not yet extracted to `src/components/`.

**Fix:** If the same pattern appears in 2+ pages, extract to
`components/`; otherwise low priority (single-use, not visually inconsistent).

#### 3.3 Stacked/multi-segment progress is intentionally custom
- `components/planning/CapacityBars.tsx`, `components/ProjectGanttView.tsx`
  do NOT use the shared `ProgressBar` (single-segment only) — this is a
  documented exception (see `ProgressBar.tsx` JSDoc), not a bug. Don't "fix"
  these into `ProgressBar` without a new component that supports segments.

#### 3.4 Check before adding a new visual primitive
Before writing a new badge/progress-bar/empty-state/color-picker/tooltip,
check the shared component table above first — F91 consolidated all of these
into `components/`. A new one-off copy is now almost always the wrong move.

### Phase 4: Refactor to Shared Layer

**Order of operations (least risk → most risk) — generic recipe for future audits.**
Items 1-6 were the F91 UI Consistency Pass scope and are done (status badges,
severity/color centralization, FilterBar, EmptyState, tooltip consolidation
into `DarkTooltip`, TeamBadge/RiceScoreBadge CSS); reuse this order for the
*next* round of duplication rather than re-doing these:

1. **Replace plain-text rendering with the shared badge component** (StatusBadge/SeverityBadge/RoleBadge/GradeBadge) — zero-risk, visual improvement
2. **Replace page-local card/badge functions with shared components** — low risk
3. **Centralize new color literals in `constants/colors.ts`** — before adding any hex to a component
4. **Migrate custom filter UI to `FilterBar` + `MultiSelectDropdown`/`SingleSelectDropdown`** — medium risk, test thoroughly
5. **Replace page-local empty-state markup with `EmptyState`** — low risk
6. **Replace page-local dark tooltips with `DarkTooltip`; keep light hover-cards on `HoverInfoCard`** — medium risk, verify positioning
7. **Convert new inline-style badges to CSS classes** — low risk but many files

### Phase 5: Verify on Key Pages

After each change, visually verify these pages (desktop only):

```
/board           — Board (most complex, ~1460 lines CSS)
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

### R7: Timeline uses muted `TIMELINE_*` tokens — never brighten
- Gantt bars/phases on Timeline intentionally use a dimmer palette than the
  equivalent Board colors — this is a deliberate design decision (F91), not
  an oversight to "fix" by matching Board brightness.
- Source of truth: `TIMELINE_PHASE_TINT`, `TIMELINE_PHASE_TINT_ROUGH`,
  `TIMELINE_ROLE_BORDER_TINT`, `TIMELINE_BAR_TRACK`, `TIMELINE_FLAGGED_BORDER`,
  `TIMELINE_BLOCKED_BORDER`, `TIMELINE_ROUGH_BG`, `TIMELINE_ROUGH_BADGE_BG`,
  `TIMELINE_ROUGH_BADGE_TEXT` in `constants/colors.ts`.
- **NEVER** brighten/replace these with full-saturation Board colors without
  an explicit redesign request.
- Exception: chart/timeline **segments** that use status color as data (bar
  fills, not labels) use `resolveStatusBgColor(status, statusStyles)` from
  `StatusBadge.tsx` — precedents `DsrBreakdownChart`, F87 `StoryBar`. Status
  **labels/badges** still go through `StatusBadge` per R1.

---

## Anti-Patterns

| Anti-Pattern | Example | Correct Approach |
|-------------|---------------------|------------------|
| Plain-text status rendering | any `{issue.status}` / `{task.status}` string | `<StatusBadge status={...} />` |
| Page-local role/grade badge | inline role or grade pill in a page | `<RoleBadge role={...} />` / `<GradeBadge grade={...} />` |
| Page-local progress bar | one-off `<div style={{width: pct+'%'}}>` | `<ProgressBar value={...} ariaLabel={...} />` (unless stacked/multi-segment — see R2/Phase 3.3) |
| Page-local empty state | Every page rolling its own "no data" div | Shared `EmptyState` component |
| Page-local color picker popup | New popover color swatch grid | Shared `ColorPicker` component |
| Page-local dark tooltip | New portal/fixed-position navy tooltip | `DarkTooltip` (unless it's a light hover-card — use `HoverInfoCard` instead) |
| New hardcoded hex color literal | `color="#dc2626"` etc. in a component | Token from `constants/colors.ts` |
| `color + '20'` opacity pattern | string-concat alpha hack | `hexToRgba(color, alpha)` from `constants/colors.ts` |
| Custom filter UI | own filter implementation instead of shared | Use `FilterBar` + `MultiSelectDropdown` |
| Brightened Timeline colors | matching Board's saturated palette on Timeline | Use `TIMELINE_*` muted tokens — see R7 |
| Icon-only button without label | `<button onClick={...}><Icon /></button>` | Add `aria-label` |
| `alert()` for form errors | `alert('Invalid value')` | Inline error message in the form |

---

## Code Review Checklist

When reviewing a PR that touches UI:

- [ ] **StatusBadge used?** — Any status string MUST go through StatusBadge
- [ ] **TeamBadge used?** — Team name MUST use TeamBadge or team.color
- [ ] **RoleBadge/GradeBadge used?** — Not a page-local role or grade pill
- [ ] **ProgressBar used?** — Not a page-local single-segment progress div (stacked bars are the documented exception)
- [ ] **EmptyState used?** — Not a page-local "no data" div
- [ ] **ColorPicker used?** — Not a new page-local color popover
- [ ] **DarkTooltip used for dark/portal tooltips?** — Light hover-cards still use `HoverInfoCard`
- [ ] **No new inline styles for permanent patterns?** — colors, padding, fontSize in `style={{}}`
- [ ] **No hardcoded hex colors in TSX?** — Use constants from `constants/colors.ts`, contexts, or CSS variables
- [ ] **No `color + '20'` alpha hack?** — Use `hexToRgba()` from `constants/colors.ts`
- [ ] **No page-local component that duplicates shared one?** — Check `src/components/` first
- [ ] **FilterBar used for filters?** — Not custom divs with dropdowns
- [ ] **Role colors from getRoleColor()?** — Not hardcoded SA=#1558BC, DEV=#803FA5, QA=#206A83
- [ ] **Issue icons from getIssueIcon()?** — Not local icon imports
- [ ] **Timeline colors from TIMELINE_* tokens?** — Not brightened to match Board (R7)
- [ ] **Language consistent?** — No RU labels in English page (except Jira data, and landing/GuidePage which are bilingual by design)
- [ ] **Empty state handled?** — Loading, error, and empty states present
- [ ] **Icon-only buttons have aria-label?** — Modal close, icon buttons, etc.
- [ ] **No `alert()` for validation errors?** — Inline error message instead
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

## Implementation Order (recommended, for the next duplication pass)

The 2026-03-08 Desktop UI Consistency pass and the F91 UI Consistency Pass
(2026-07-11) already executed this recipe against the whole app (status
badges, DSR/severity colors, FilterBar, EmptyState, tooltips, role/grade
badges, progress bars, color pickers, a11y, RU→EN, CSS hygiene — see
`ai-ru/features/F91_UI_CONSISTENCY.md`). Reuse this order for whatever new
duplication the next audit finds — don't assume these specific line items
still need doing:

**Phase 1 — Quick wins (zero risk):**
1. Replace plain-text status/role/grade rendering with the matching shared badge
2. Replace page-local card/badge functions with shared components

**Phase 2 — Color centralization (low risk):**
3. Extract new color literals to `constants/colors.ts`
4. Replace `color + 'NN'` alpha hacks with `hexToRgba()`

**Phase 3 — Component extraction (medium risk):**
5. Extract new page-local badges/cards to `components/`
6. Migrate custom empty states to `EmptyState`
7. Migrate custom filters to `FilterBar`

**Phase 4 — Style migration (medium risk):**
8. Convert new inline-style badges to CSS classes
9. Consolidate new tooltip patterns into `DarkTooltip` (dark/portal) or `HoverInfoCard` (light/anchored)

**Phase 5 — Language cleanup (low risk):**
10. Standardize app page labels to English (exception: landing, GuidePage)
11. Update tests for translated strings

**Phase 6 — a11y (low risk):**
12. `aria-label` on icon-only buttons
13. Replace `alert()` with inline errors
14. Visually-hidden (not `display:none`) for keyboard-focusable elements

---

## Key File References

### Sources of Truth (extend, never duplicate)
| File | Role |
|------|------|
| `components/board/StatusBadge.tsx` | Status rendering (`maxWidth` prop for truncation) |
| `components/board/StatusStylesContext.tsx` | Status colors from backend |
| `contexts/WorkflowConfigContext.tsx` | Workflow config, role colors, issue icons |
| `components/TeamBadge.tsx` | Team badge with color |
| `components/RoleBadge.tsx` | Member role pill (F91) |
| `components/GradeBadge.tsx` | Member seniority pill (F91) |
| `components/ProgressBar.tsx` | Single-segment progress bar with ARIA (F91) |
| `components/EmptyState.tsx` | Unified empty state (F91) |
| `components/ColorPicker.tsx` | Popover color picker (F91) |
| `components/DarkTooltip.tsx` | Portal dark tooltip for Timeline (F91) |
| `components/SeverityBadge.tsx` | Severity badge, re-exports `SEVERITY_COLORS` |
| `components/metrics/MetricCard.tsx` | Metric cards |
| `components/FilterBar.tsx` | Filter container |
| `components/FilterChips.tsx` | Filter chips |
| `components/Modal.tsx` | Modal dialogs |
| `components/Skeleton.tsx` | Loading skeletons |
| `helpers/priorityColors.ts` | Priority color mapping |
| `constants/teamColors.ts` | Team color palette |
| `constants/colors.ts` | Every other color token — DSR, severity, grade, absence, timeline (muted, see R7), tooltip, text hierarchy, `hexToRgba()` helper |
| `components/board/helpers.ts` | Issue icons, formatCompact |
| `App.css` | Global styles, .btn classes, loading/error/empty |

### Deliberately custom (not a duplication bug — do not "fix")
| File | Why it's custom |
|------|------|
| `components/planning/CapacityBars.tsx` | Stacked/multi-segment capacity indicator — `ProgressBar` is single-segment only |
| `components/ProjectGanttView.tsx` | Gantt bars positioned by date range with status-driven coloring, not a linear progress fill |
| `components/board/StatusBadge.tsx` (`resolveStatusBgColor`) used directly in `DsrBreakdownChart`, F87 `StoryBar` | Chart/timeline segments use status color as data (bar fill), not a label — see design-system.md carve-out |
| `pages/landing/`, `pages/GuidePage.tsx` | RU / bilingual by design (R5 exception), not a language-consistency bug |

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
