CREATE INDEX IF NOT EXISTS idx_worklogs_author_date
    ON issue_worklogs(author_account_id, started_date);
