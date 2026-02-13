-- V7: Change rough estimate to dynamic per-role JSONB
-- Drop old single rough estimate column
ALTER TABLE jira_issues DROP COLUMN IF EXISTS rough_estimate_days;

-- Add JSONB column for dynamic role-based estimates (e.g. {"SA": 5, "DEV": 10, "QA": 3})
ALTER TABLE jira_issues ADD COLUMN rough_estimates JSONB;

-- Keep the metadata columns (already exist from V6)
-- rough_estimate_updated_at and rough_estimate_updated_by
