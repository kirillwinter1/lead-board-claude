-- V47: Add labels and manual_boost columns for quarterly capacity planning (F55)
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'jira_issues' AND table_schema = current_schema()) THEN
    ALTER TABLE jira_issues ADD COLUMN IF NOT EXISTS labels TEXT[];
    ALTER TABLE jira_issues ADD COLUMN IF NOT EXISTS manual_boost INTEGER DEFAULT 0;
    CREATE INDEX IF NOT EXISTS idx_jira_issues_labels ON jira_issues USING GIN(labels);
  END IF;
END $$;
