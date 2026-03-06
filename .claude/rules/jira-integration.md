# Jira Integration Rules

## Jira is the SINGLE Source of Truth

When creating issues: create in Jira FIRST, then save locally. Never store task data that contradicts Jira.

## JiraConfigResolver — ALWAYS

**ALWAYS use `JiraConfigResolver`** for Jira configuration. NEVER use `JiraProperties` directly. JiraConfigResolver reads from tenant_jira_config (DB) for tenants, falls back to .env (JiraProperties) for single-tenant mode.

## No Hardcoding

- **NEVER hardcode roles, issue types, or statuses.** Use `WorkflowConfigService` methods: `isStory()`, `isEpic()`, `isBug()`, `isStoryOrBug()`, `getStoryTypeName()`, `getBugTypeNames()`.
- **NEVER write `"SA"`, `"DEV"`, `"QA"`, `"Epic"`, `"Story"` as constants** in business logic.
- **Subtask is NOT a standalone task** — it always belongs to a parent (Story/Task/Bug).

## Known Issues

- **Jira API 410 Gone:** Use `/rest/api/3/search/jql` instead of `/rest/api/3/search`
- **Jira JQL:** `m` = minutes (NOT months!). For months: use `months * 30` -> `-180d`
- **Role mapping with Cyrillic:** Use fallback via `String.contains()`
