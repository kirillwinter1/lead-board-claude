-- Add Atlassian Team ID for syncing teams from Atlassian Teams API
ALTER TABLE teams ADD COLUMN atlassian_team_id VARCHAR(100);

-- Index for looking up teams by Atlassian ID
CREATE INDEX idx_teams_atlassian_team_id ON teams(atlassian_team_id);
