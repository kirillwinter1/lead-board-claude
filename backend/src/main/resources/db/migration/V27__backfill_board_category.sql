-- V27: Backfill board_category and workflow_role for existing jira_issues
-- V26 added the columns but didn't populate them for existing records

-- Populate board_category from issue_type_mappings
UPDATE jira_issues ji
SET board_category = itm.board_category
FROM issue_type_mappings itm
WHERE ji.board_category IS NULL
  AND LOWER(ji.issue_type) = LOWER(itm.jira_type_name);

-- Populate workflow_role for subtasks from issue_type_mappings
UPDATE jira_issues ji
SET workflow_role = itm.workflow_role_code
FROM issue_type_mappings itm
WHERE ji.workflow_role IS NULL
  AND LOWER(ji.issue_type) = LOWER(itm.jira_type_name)
  AND itm.workflow_role_code IS NOT NULL;

-- Fallback: any remaining subtasks without board_category
UPDATE jira_issues
SET board_category = 'SUBTASK'
WHERE board_category IS NULL
  AND is_subtask = TRUE;

-- Fallback: any remaining non-subtasks without board_category (treat as STORY)
UPDATE jira_issues
SET board_category = 'STORY'
WHERE board_category IS NULL
  AND is_subtask = FALSE;
