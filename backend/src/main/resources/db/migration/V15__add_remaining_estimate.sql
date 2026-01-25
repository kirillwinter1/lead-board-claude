-- Add remaining estimate field for time tracking
ALTER TABLE jira_issues ADD COLUMN remaining_estimate_seconds BIGINT;

COMMENT ON COLUMN jira_issues.remaining_estimate_seconds IS 'Remaining estimate in seconds from Jira time tracking';
