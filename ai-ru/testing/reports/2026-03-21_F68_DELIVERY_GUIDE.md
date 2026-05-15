# QA Report: F68 Delivery Guide
**Date:** 2026-03-21
**Tester:** Claude QA Agent

## Summary
- Overall: **PASS WITH ISSUES**
- Unit tests: 5 passed, 0 failed (GuidePage.test.tsx)
- Frontend build: PASS (tsc + vite build, no errors)
- Backend tests: 860 passed, 73 failed (all pre-existing, unrelated to F68)
- Visual: 3 issues found (1 Medium, 2 Low)
- Bugs fixed: 0

## Test Results

### 1. Frontend Unit Tests
**Command:** `npx vitest run src/pages/GuidePage.test.tsx`
**Result:** 5/5 passed

| Test | Result |
|------|--------|
| Renders without crash | PASS |
| Renders all pipeline stages in sidebar | PASS |
| Switches language on toggle click | PASS |
| Renders pipeline visual with all stages | PASS |
| Renders roles section | PASS |

### 2. Frontend Build
**Command:** `npm run build`
**Result:** PASS (tsc + vite build in 2.24s, no errors)

### 3. Backend Tests
**Command:** `./gradlew test`
**Result:** 933 tests, 73 failed, 3 skipped
**Note:** All 73 failures are pre-existing (Board, Forecast, Metrics, Sync component tests). F68 has no backend code changes (only version bump in build.gradle.kts). Not relevant to this feature.

## Content Verification

### All 8 Stages Present
| Stage | ID | Goal | Roles | Screen Links | Checklist | Anti-patterns |
|-------|-----|------|-------|-------------|-----------|---------------|
| 1. Idea | pipeline-idea | Yes | PO, DM, TL | Board | 3 items | 2 items |
| 2. BRD | pipeline-brd | Yes | PO, SA, TL | Board | 3 items | 2 items |
| 3. Rough Estimates | pipeline-rough-estimates | Yes | TL, Roles, PO | Board, Timeline, Projects | 2 items | 3 items |
| 4. Planning | pipeline-planning | Yes | TL, Roles, PO, DM | Board, DQ, Poker, QP | 4 items | 4 items |
| 5. Development | pipeline-development | Yes | Executors, TL, DM | Board, Teams, Timeline, Metrics | 2 items | 4 items |
| 6. E2E (opt.) | pipeline-e2e | Yes | QA, TL | Bug Metrics | 1 item | None |
| 7. Acceptance (opt.) | pipeline-acceptance | Yes | PO, TL, DM | None | 1 item | None |
| 8. Done | pipeline-done | Yes | TL, DM, DevOps | Board, Timeline, Metrics | 3 items | None |

### All 5 Roles Present
| Role | ID | Stages Table | Key Screens |
|------|-----|-------------|-------------|
| Product Owner (PO) | roles-po | 5 stages | Board, Projects, QP |
| Delivery Manager (DM) | roles-dm | 4 stages | Metrics, Board, Timeline, Bug Metrics |
| Team Lead (TL) | roles-tl | 8 stages | Board, DQ, Timeline, Teams |
| Pipeline Executors | roles-executors | 4 stages | Board, Teams, Poker |
| DevOps | roles-devops | 1 stage | (external) |

### Rules Section Content
| Section | Present |
|---------|---------|
| Task hierarchy (Epic > Story > Subtask) | Yes |
| Estimation rules (5 rules in table) | Yes |
| Time logging (3 rules in table) | Yes |
| Grades (Senior 0.8, Middle 1.0, Junior 1.5) | Yes |
| Rough estimates description | Yes |
| Flags & Pauses (Epic -100, Story -200) | Yes |
| WIP limits | Yes |

### Em Dash Check
- Guide content files (`frontend/src/guide/content/`): **No em dashes found**
- Components: 1 em dash in `GuideSection.tsx:54` (ScreenLinks separator `' -- '`) -- see Medium issue below

### Links to Lead Board Screens
All links use React Router `<Link>` and resolve to valid routes:
- `/` (Board) -- used in stages 1, 2, 3, 4, 5, 8 and roles
- `/?view=timeline` (Timeline) -- used in stages 3, 5, 8 and roles
- `/projects` (Projects) -- used in stage 3 and roles
- `/data-quality` (Data Quality) -- used in stage 4 and roles
- `/poker` (Planning Poker) -- used in stage 4 and roles
- `/quarterly-planning` (Quarterly Planning) -- used in stage 4 and roles
- `/metrics` (Metrics) -- used in stages 5, 8 and roles
- `/teams` (Teams) -- used in stages 5 and roles
- `/bug-metrics` (Bug Metrics) -- used in stage 6 and roles
- `/workflow` (Workflow Config) -- used in rules and roles sections

## Navigation Testing

| Test | Result | Notes |
|------|--------|-------|
| Sidebar item scrolls to section | PASS | Click "1. Idea" scrolls to pipeline-idea section |
| URL hash updates on navigate | PASS | URL changes to `#pipeline-idea` etc. |
| Pipeline diagram stage click | PASS | Clicking stage box triggers scroll |
| Language toggle switches all content | PASS | Sidebar, content, pipeline labels all switch |
| Active sidebar item highlights | PASS | Blue background on active item |
| Collapsible sidebar sections | PASS | Pipeline/Roles sections toggle expand/collapse |
| Language persisted in localStorage | PASS | Uses key `guide-lang` |
| Deep link on page load | PASS | Hash fragment processed on initial load |

## Visual Review

### Screenshots Taken
1. `f68_qa_full_page_ru.png` -- Full page in Russian (default)
2. `f68_qa_pipeline_diagram.png` -- Pipeline visual component close-up
3. `f68_qa_stage_idea.png` -- Stage 1 "Idea" with sidebar active
4. `f68_qa_en_version.png` -- English version after language switch
5. `f68_qa_roles_po.png` -- Roles section (Executors + DevOps)
6. `f68_qa_mobile_view.png` -- Mobile viewport (375x812)

### Visual Observations
- Layout is clean, sidebar fixed at 260px, content max-width 900px
- Pipeline diagram shows all 8 stages with arrows between them
- Optional stages (E2E, Acceptance) have dashed borders as specified
- Quality control bar spans full width below stages
- Role cards have green name headers, clear separation
- Checklist items show checkbox symbols
- Anti-patterns have red-tinted background
- Tables are well-formatted with proper column headers

## Bugs Found

### Medium
1. **Em dash in ScreenLinks separator** -- `GuideSection.tsx:54` uses `' -- '` (em dash) as separator between screen link name and description. Per the task spec, em dashes should have been removed (commit `11fb449` "remove AI patterns - em dashes"). This one was missed. All instances in the rendered page show the em dash pattern (e.g., "Board -- Epic in NEW status").

### Low
1. **Mobile sidebar not collapsed** -- The spec states "Sidebar collapses to hamburger menu at < 768px breakpoint" but the actual implementation simply stacks the sidebar vertically above the content at that breakpoint. The sidebar takes up significant vertical space (~70% of viewport) before content begins on mobile. Users on small screens must scroll past the entire navigation to reach content.

2. **Console warning: "No routes matched location /guide"** -- React Router emits a warning `No routes matched location "/guide"` when the page renders. This appears to be a transient warning during Link processing within the guide content. It does not affect functionality but pollutes the console.

## Test Coverage Gaps

1. **No test for pipeline diagram click navigation** -- Tests verify rendering but not the click-to-scroll behavior of pipeline stage boxes
2. **No test for deep link scrolling** -- The `useEffect` that scrolls to hash fragment on page load is not tested
3. **No test for IntersectionObserver scroll tracking** -- The active section tracking via IntersectionObserver is mocked out but not tested for correctness
4. **No test for localStorage persistence** -- Language choice persistence is not verified across page reloads
5. **No test for screen link validity** -- Internal `<Link>` destinations are not verified against actual routes
6. **No mobile/responsive tests** -- No test for the < 768px sidebar behavior
7. **No accessibility tests** -- Keyboard navigation, screen reader compatibility not verified
