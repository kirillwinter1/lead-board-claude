# F64 Multi-Project Support

**Version:** 0.64.0
**Date:** 2026-03-06

## Problem

Lead Board supported only a single Jira project key per tenant. Organizations with multiple projects (e.g., backend + frontend + mobile) couldn't see cross-project data on the Board, Timeline, or Data Quality pages.

## Solution

### Backend

- **`jira_projects` table** — stores active project keys per tenant with sync metadata
- **`JiraProjectEntity`** — entity with `projectKey`, `name`, `active`, `lastSyncedAt`
- **`JiraProjectRepository.getActiveProjectKeys()`** — returns list of active keys
- **`SyncService`** — iterates all active project keys during sync
- **`BoardService`** — aggregates issues across all active projects
- **`DataQualityService`** — validates issues across all projects
- **Migration T9:** `jira_projects` table creation

### Frontend

- **Project filter** on Board page — filter by specific project
- **Project selector** in Settings → Projects — CRUD for active projects
- **Multi-project aware** search, filters, and data quality

### API

- `GET /api/admin/projects` — list active projects
- `POST /api/admin/projects` — add project
- `DELETE /api/admin/projects/{key}` — remove project
- `GET /api/board?projectKey=X` — filter board by project

## Testing

- Backend: JiraProjectServiceTest (CRUD, active filtering)
- Frontend: SettingsPage project management UI verified via screenshots
