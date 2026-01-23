-- V7: Change rough estimate to per-role estimates (SA, DEV, QA)
-- Drop old single rough estimate column
ALTER TABLE jira_issues DROP COLUMN IF EXISTS rough_estimate_days;

-- Add per-role rough estimate columns
ALTER TABLE jira_issues ADD COLUMN rough_estimate_sa_days DECIMAL(10,1);
ALTER TABLE jira_issues ADD COLUMN rough_estimate_dev_days DECIMAL(10,1);
ALTER TABLE jira_issues ADD COLUMN rough_estimate_qa_days DECIMAL(10,1);

-- Keep the metadata columns (already exist from V6)
-- rough_estimate_updated_at and rough_estimate_updated_by
