-- Add team_field_value to cache the Jira Team field value
ALTER TABLE jira_issues ADD COLUMN team_field_value VARCHAR(255);

-- Add team_id for direct mapping to teams table
ALTER TABLE jira_issues ADD COLUMN team_id BIGINT REFERENCES teams(id);

-- Index for faster team lookups
CREATE INDEX idx_jira_issues_team_id ON jira_issues(team_id);
