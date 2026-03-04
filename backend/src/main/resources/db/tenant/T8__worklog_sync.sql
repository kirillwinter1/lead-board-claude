-- F57: Jira Worklog Sync — per-day role coloring on Timeline (tenant)
CREATE TABLE IF NOT EXISTS issue_worklogs (
    id              BIGSERIAL PRIMARY KEY,
    issue_key       VARCHAR(50) NOT NULL REFERENCES jira_issues(issue_key) ON DELETE CASCADE,
    worklog_id      VARCHAR(50) NOT NULL,
    author_account_id VARCHAR(255),
    time_spent_seconds INTEGER NOT NULL DEFAULT 0,
    started_date    DATE NOT NULL,
    role_code       VARCHAR(50),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_issue_worklog UNIQUE (issue_key, worklog_id)
);

CREATE INDEX idx_worklogs_issue_key ON issue_worklogs(issue_key);
CREATE INDEX idx_worklogs_started_date ON issue_worklogs(started_date);
