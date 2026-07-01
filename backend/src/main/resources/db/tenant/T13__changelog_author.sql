-- F80: author of status transition (who moved the issue), for closedBy metric (tenant schema).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = current_schema() AND table_name = 'status_changelog') THEN
        ALTER TABLE status_changelog ADD COLUMN IF NOT EXISTS author_account_id VARCHAR(255);
        CREATE INDEX IF NOT EXISTS idx_status_changelog_author ON status_changelog(author_account_id);
    END IF;
END $$;
