-- F84: Data Quality auto-fix.
-- The EPIC_NO_TEAM fix assigns a team locally (no Jira team field write), so the
-- next sync would recompute team_id from the Jira team field and null it again.
-- This flag marks a manually-assigned team so sync preserves it while the Jira
-- team field stays empty; the flag is cleared as soon as Jira resolves a team.
ALTER TABLE jira_issues ADD COLUMN IF NOT EXISTS team_id_manual BOOLEAN NOT NULL DEFAULT FALSE;
