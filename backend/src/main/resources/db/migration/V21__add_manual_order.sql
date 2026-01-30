-- Add manual_order column for explicit ordering of epics and stories
ALTER TABLE jira_issues ADD COLUMN manual_order INTEGER;

-- Initialize order for EPICS (by team_id, sorted by auto_score DESC)
WITH ranked AS (
  SELECT id, ROW_NUMBER() OVER (PARTITION BY team_id ORDER BY auto_score DESC NULLS LAST, issue_key) as rn
  FROM jira_issues
  WHERE issue_type IN ('Epic', 'Эпик')
)
UPDATE jira_issues SET manual_order = ranked.rn FROM ranked WHERE jira_issues.id = ranked.id;

-- Initialize order for STORIES and BUGS (by parent_key, sorted by auto_score DESC)
WITH ranked AS (
  SELECT id, ROW_NUMBER() OVER (PARTITION BY parent_key ORDER BY auto_score DESC NULLS LAST, issue_key) as rn
  FROM jira_issues
  WHERE issue_type IN ('Story', 'История', 'Bug', 'Баг')
)
UPDATE jira_issues SET manual_order = ranked.rn FROM ranked WHERE jira_issues.id = ranked.id;

-- Create indexes for efficient sorting
CREATE INDEX idx_jira_issues_team_manual_order ON jira_issues(team_id, manual_order);
CREATE INDEX idx_jira_issues_parent_manual_order ON jira_issues(parent_key, manual_order);
