-- Jira issues cache table
CREATE TABLE jira_issues (
    id BIGSERIAL PRIMARY KEY,
    issue_key VARCHAR(50) NOT NULL UNIQUE,
    issue_id VARCHAR(50) NOT NULL,
    project_key VARCHAR(50) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    status VARCHAR(100) NOT NULL,
    issue_type VARCHAR(100) NOT NULL,
    is_subtask BOOLEAN NOT NULL DEFAULT FALSE,
    parent_key VARCHAR(50),
    original_estimate_seconds BIGINT,
    time_spent_seconds BIGINT,
    jira_updated_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_jira_issues_project_key ON jira_issues(project_key);
CREATE INDEX idx_jira_issues_issue_type ON jira_issues(issue_type);
CREATE INDEX idx_jira_issues_parent_key ON jira_issues(parent_key);
CREATE INDEX idx_jira_issues_status ON jira_issues(status);

-- Sync state table
CREATE TABLE jira_sync_state (
    id BIGSERIAL PRIMARY KEY,
    project_key VARCHAR(50) NOT NULL UNIQUE,
    last_sync_started_at TIMESTAMP WITH TIME ZONE,
    last_sync_completed_at TIMESTAMP WITH TIME ZONE,
    last_sync_issues_count INTEGER DEFAULT 0,
    sync_in_progress BOOLEAN NOT NULL DEFAULT FALSE,
    last_error VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
