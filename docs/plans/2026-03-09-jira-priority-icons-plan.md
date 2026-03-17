# Implementation Plan: F66 Jira Priority Icons

Feature: F66 | Version: 0.66.0

## Step 1: Backend — Add `priority` field to BoardNode

**Files:**
- `backend/src/main/java/com/leadboard/board/BoardNode.java` — add `private String priority` field + getter/setter
- `backend/src/main/java/com/leadboard/board/BoardService.java` — in `mapToNode()` method, add `node.setPriority(entity.getPriority())`

**Verify:** Backend compiles, existing tests pass.

## Step 2: Backend — Add priorities metadata endpoint

**Files:**
- `backend/src/main/java/com/leadboard/config/service/JiraMetadataService.java` — add `getPriorities()` method:
  - Call Jira API `GET /rest/api/3/priority`
  - Extract: `id`, `name`, `iconUrl`
  - Cache with same TTL pattern as issue types
- `backend/src/main/java/com/leadboard/config/controller/JiraMetadataController.java` — add endpoint:
  - `@GetMapping("/priorities")` → `metadataService.getPriorities()`

**Verify:** `curl http://localhost:8080/api/admin/jira-metadata/priorities` returns list with iconUrl.

## Step 3: Frontend — Add priority to BoardNode type

**Files:**
- `frontend/src/components/board/types.ts` — add `priority: string | null` to `BoardNode` interface

## Step 4: Frontend — Load priority icons into WorkflowConfigContext

**Files:**
- `frontend/src/contexts/WorkflowConfigContext.tsx`:
  - Add `priorityIcons: Record<string, string>` to `WorkflowConfig` type
  - Load `/api/admin/jira-metadata/priorities` in parallel with existing calls
  - Build `priorityIcons` map (name → iconUrl)
  - Add `getPriorityIconUrl(priorityName: string): string | null` helper

## Step 5: Frontend — Render priority icon in BoardRow

**Files:**
- `frontend/src/components/board/BoardRow.tsx`:
  - Import `getPriorityIconUrl` from context
  - Add `<img>` with priority icon after issue type icon, before issue key
  - Size: 16x16, with `title={node.priority}` tooltip
  - Only render if `node.priority` is not null

## Step 6: Version bump

**Files:**
- `backend/build.gradle.kts` — version → `0.66.0`
- `frontend/package.json` — version → `0.66.0`

## Step 7: Tests

**Files:**
- `backend/src/test/java/com/leadboard/board/BoardServiceTest.java` — verify priority is mapped to BoardNode

## Step 8: Documentation

**Files:**
- `ai-ru/features/F66_PRIORITY_ICONS.md` — feature spec
- `ai-ru/FEATURES.md` — add F66 to table

## Review Checkpoints

- After Step 2: verify API returns correct data
- After Step 5: visual check on board — icons visible and aligned
