CREATE TABLE flag_changelog (
    id BIGSERIAL PRIMARY KEY,
    issue_key VARCHAR(50) NOT NULL,
    flagged_at TIMESTAMP WITH TIME ZONE NOT NULL,
    unflagged_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_flag_changelog_issue_key ON flag_changelog(issue_key);
