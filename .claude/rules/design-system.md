# Design System Rules

These rules apply to ALL features without exception.

## Visual Data — Only from Configuration

1. **Issue type icons** — ALWAYS `getIssueIcon(type, getIssueTypeIconUrl(type))`. NEVER import local icons directly.
2. **Status colors** — from `StatusStylesContext` / `StatusBadge`. NEVER use hardcoded `STATUS_COLORS` palettes.
3. **Team colors** — `TeamBadge` or `team.color`. Never show team name without its color.
4. **Phase/role colors** — `getRoleColor(code)` from `WorkflowConfigContext`. NEVER hardcode SA/DEV/QA colors.

## Component Reuse — No Duplication

5. **Ready components**: `StatusBadge`, `TeamBadge`, `RiceScoreBadge`, `Modal`, `MultiSelectDropdown` — use as-is. Never create duplicates.
6. **Ready helpers**: `getIssueIcon()`, `getStatusStyles()`, `getIssueTypeIconUrl()`, `getRoleColor()` — never write your own versions, extend existing.
7. **Before creating a new component** — check `components/`. If similar exists — extend it, don't create a new one.

## StatusBadge — The ONLY Way to Render Statuses

`StatusBadge` reads colors from `StatusStylesContext`, applies correct background + contrast text color, handles CSS class fallback. NEVER reproduce this logic manually.

```tsx
import { StatusBadge } from '../board/StatusBadge'
<StatusBadge status={statusName} />
```

NEVER: manually read useStatusStyles(), apply opacity hacks, use getContrastColor() outside StatusBadge, hardcode status color values, use foreignObject in SVG.

## Charts with Rich Labels — Two-Column HTML Layout

When building charts that need rich labels (icons, badges, links), NEVER use Recharts YAxis with custom SVG ticks or foreignObject. Use a two-column HTML layout:

- Left column: plain HTML with React components (StatusBadge, TeamBadge, links, icons)
- Right column: Recharts chart without YAxis, rows aligned by matching heights

Reference: `DsrBreakdownChart.tsx`, `TimelinePage.tsx` (EpicLabel component).
