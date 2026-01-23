-- V6: Add rough estimate fields for Epics
-- These are local Lead Board data that survive Jira sync

ALTER TABLE jira_issues ADD COLUMN rough_estimate_days DECIMAL(10,1);
ALTER TABLE jira_issues ADD COLUMN rough_estimate_updated_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE jira_issues ADD COLUMN rough_estimate_updated_by VARCHAR(255);
