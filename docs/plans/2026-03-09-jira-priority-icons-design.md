# F66: Jira Priority Icons on Board

## Summary

Display Jira issue priority as an icon next to the issue type icon on the board.
Follow the existing pattern for issue type icons (JiraMetadataService → endpoint → WorkflowConfigContext → BoardRow).

## Scope

- Only on BoardRow (board page)
- Priority icon shown between issue type icon and issue key
- Icons fetched from Jira metadata API, with tooltip showing priority name

## Architecture

Same pattern as issue type icons:

```
Jira API GET /rest/api/3/priority
  → JiraMetadataService.getPriorities()
    → JiraMetadataController GET /api/admin/jira-metadata/priorities
      → WorkflowConfigContext: priorityIcons + getPriorityIconUrl()
        → BoardRow: <img> with priority icon
```

## Data Flow

1. **Backend** adds `priority` field to BoardNode, maps from JiraIssueEntity
2. **Backend** adds metadata endpoint for priority list with iconUrl
3. **Frontend** loads priority metadata into WorkflowConfigContext
4. **Frontend** renders priority icon in BoardRow

## Design Decisions

- No DB migration needed — priority already stored in jira_issues.priority (VARCHAR 50)
- No new DB column for icon URL — fetched dynamically from Jira metadata (same as issue types)
- Tooltip on hover shows priority name (e.g., "Highest", "Medium")
- Icon size matches issue type icon (16x16)
