-- F80: author of status transition (who moved the issue), for closedBy metric.
ALTER TABLE status_changelog ADD COLUMN IF NOT EXISTS author_account_id VARCHAR(255);
CREATE INDEX IF NOT EXISTS idx_status_changelog_author ON status_changelog(author_account_id);
