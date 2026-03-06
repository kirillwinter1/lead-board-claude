# Frontend Engineer Memory

## Shared Components

### ViewToggle (`components/ViewToggle.tsx`)
Segmented control for switching between related page views. Used for Projects (List/Gantt). Takes `options: { path, label }[]`. Styled as pill-shaped buttons with #0052CC active / #F4F5F7 inactive.

## Layout Navigation
- When two pages share a concept (e.g., Projects list + Gantt), use a single nav tab that covers both routes. In Layout.tsx, override `className` with manual `location.pathname` check instead of `isActive` to make the tab active on both paths.

## Key Patterns
- `SingleSelectDropdown` — accepts `options: { value, label }[]`, `selected: string | null`, `onChange: (v: string | null) => void`. Use `allowClear={false}` for mandatory selections.
- `MultiSelectDropdown` — accepts `options: string[]`, `selected: Set<string>`, `onToggle: (v: string) => void`. Use `colorMap: Map<string, string>` for colored dots.
- `FilterBar` — wraps filter controls + chips. Children are the filter dropdowns. `chips` + `onClearAll` for active filter display.
- `FilterChip` — `{ category, value, color?, onRemove }`.

## Team Data in Projects
- `ProjectDto` does NOT have team info — team filter not feasible on list view.
- `ProjectTimelineDto.epics[].teamName/teamColor` — team data available in timeline/Gantt view.
- Extract unique team names/colors from epics for team filter in ProjectTimelinePage.

## Locale Conventions
- ProjectTimelinePage uses `en-US` locale for all dates.
- ProjectsPage still uses `ru-RU` for `formatDate` (not changed in F61).
