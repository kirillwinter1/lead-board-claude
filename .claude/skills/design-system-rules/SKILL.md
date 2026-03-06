---
name: design-system-rules
description: Lead Board Design System rules and component inventory
user-invocable: false
---

# Design System — Component Inventory

## Required Components (NEVER duplicate)

| Component | Location | Usage |
|-----------|----------|-------|
| `StatusBadge` | `components/board/StatusBadge.tsx` | Render any status — reads colors from StatusStylesContext |
| `TeamBadge` | `components/board/TeamBadge.tsx` | Render team name with accent color |
| `RiceScoreBadge` | `components/RiceScoreBadge.tsx` | Render RICE priority score |
| `Modal` | `components/Modal.tsx` | Any modal dialog |
| `MultiSelectDropdown` | `components/MultiSelectDropdown.tsx` | Multi-select with color dots |
| `FilterBar` | `components/FilterBar.tsx` | Filter panel with chips |
| `SearchInput` | `components/SearchInput.tsx` | Search input field |

## Required Helpers (NEVER rewrite)

| Helper | Location | Usage |
|--------|----------|-------|
| `getIssueIcon(type, url)` | `helpers.ts` | Issue type icon — Jira URL with local fallback |
| `getIssueTypeIconUrl(type)` | `WorkflowConfigContext` | Get Jira icon URL by type name |
| `getStatusStyles(status)` | `StatusStylesContext` | Status color from DB config |
| `getRoleColor(code)` | `WorkflowConfigContext` | Phase/role color (SA, DEV, QA) |
| `getTenantSlug()` | `helpers.ts` | Current tenant slug for multi-tenancy |

## Contexts

| Context | Purpose |
|---------|---------|
| `WorkflowConfigContext` | Workflow config, issue types, icons, role colors |
| `StatusStylesContext` | Status colors and styles from backend |

## Icon Chain

Jira API -> JiraMetadataService (cache 1h) -> GET /api/admin/jira-metadata/issue-types -> WorkflowConfigContext (issueTypeIcons) -> getIssueTypeIconUrl(typeName) -> getIssueIcon(type, jiraIconUrl)

Usage: `<img src={getIssueIcon(e.issueType, getIssueTypeIconUrl(e.issueType))} />`
