-- F77: Eisenhower Matrix MVP — manual quadrant triage for orphan tasks.
-- Quadrant values: P1 / P2 / P3 / P4 / NULL (unassigned).
-- The column is owned by Lead Board (not Jira) and survives sync upserts.
ALTER TABLE jira_issues ADD COLUMN eisenhower_quadrant VARCHAR(10);

-- Partial index: the matrix only queries orphan issues (parent_key IS NULL),
-- grouped per team and quadrant.
CREATE INDEX idx_jira_issues_quadrant ON jira_issues(team_id, eisenhower_quadrant)
  WHERE parent_key IS NULL;
