-- V14: Add assignee fields to jira_issues table
-- Support for assignee-based capacity allocation and story-level forecasting

ALTER TABLE jira_issues
  ADD COLUMN assignee_account_id VARCHAR(255),
  ADD COLUMN assignee_display_name VARCHAR(255),
  ADD COLUMN started_at TIMESTAMP WITH TIME ZONE;

-- Index for filtering by assignee
CREATE INDEX idx_jira_issues_assignee
  ON jira_issues(assignee_account_id)
  WHERE assignee_account_id IS NOT NULL;

-- Index for finding stories in progress
CREATE INDEX idx_jira_issues_started_at
  ON jira_issues(started_at)
  WHERE started_at IS NOT NULL;

COMMENT ON COLUMN jira_issues.assignee_account_id IS 'Jira account ID of the assignee (from assignee.accountId)';
COMMENT ON COLUMN jira_issues.assignee_display_name IS 'Display name of the assignee for UI (from assignee.displayName)';
COMMENT ON COLUMN jira_issues.started_at IS 'Timestamp when work started (when status changed to In Progress)';
