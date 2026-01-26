-- V17: Create status_changelog table for tracking status transitions
-- This enables Time in Status metrics calculation

CREATE TABLE status_changelog (
    id BIGSERIAL PRIMARY KEY,
    issue_key VARCHAR(50) NOT NULL,
    issue_id VARCHAR(50) NOT NULL,
    from_status VARCHAR(100),
    to_status VARCHAR(100) NOT NULL,
    transitioned_at TIMESTAMP WITH TIME ZONE NOT NULL,
    time_in_previous_status_seconds BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_changelog_issue FOREIGN KEY (issue_key)
        REFERENCES jira_issues(issue_key) ON DELETE CASCADE
);

-- Index for looking up history by issue
CREATE INDEX idx_changelog_issue_key ON status_changelog(issue_key);

-- Index for time-based queries
CREATE INDEX idx_changelog_transitioned_at ON status_changelog(transitioned_at);

-- Index for status aggregations
CREATE INDEX idx_changelog_to_status ON status_changelog(to_status);

-- Unique constraint to prevent duplicate entries
CREATE UNIQUE INDEX idx_changelog_unique ON status_changelog(issue_key, to_status, transitioned_at);

COMMENT ON TABLE status_changelog IS 'Tracks status transitions for issues to calculate time-in-status metrics';
COMMENT ON COLUMN status_changelog.time_in_previous_status_seconds IS 'Time spent in the previous status before this transition';
