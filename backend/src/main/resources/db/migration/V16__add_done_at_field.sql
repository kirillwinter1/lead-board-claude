-- V16: Add done_at field for metrics calculations
-- This field tracks when an issue was completed (transitioned to Done status)

ALTER TABLE jira_issues ADD COLUMN done_at TIMESTAMP WITH TIME ZONE;

-- Index for efficient filtering by completion date
CREATE INDEX idx_jira_issues_done_at ON jira_issues(done_at);

-- Composite index for team metrics queries
CREATE INDEX idx_jira_issues_team_done ON jira_issues(team_id, done_at) WHERE done_at IS NOT NULL;

-- Composite index for assignee metrics queries
CREATE INDEX idx_jira_issues_assignee_done ON jira_issues(assignee_account_id, done_at)
    WHERE done_at IS NOT NULL AND assignee_account_id IS NOT NULL;

COMMENT ON COLUMN jira_issues.done_at IS 'Date when issue transitioned to Done status';
