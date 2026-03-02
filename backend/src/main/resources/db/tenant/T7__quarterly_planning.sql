-- T7: Add labels and manual_boost columns for quarterly capacity planning (F55)
ALTER TABLE jira_issues ADD COLUMN IF NOT EXISTS labels TEXT[];
ALTER TABLE jira_issues ADD COLUMN IF NOT EXISTS manual_boost INTEGER DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_jira_issues_labels ON jira_issues USING GIN(labels);
