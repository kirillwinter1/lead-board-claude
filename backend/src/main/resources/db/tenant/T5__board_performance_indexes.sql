-- T5: Board performance indexes
-- Covering indexes for ORDER BY elimination + composite for new queries

-- 1. Replace (board_category, team_id) with covering index including manual_order
--    Eliminates in-memory sort for findEpicsByTeamOrderByManualOrder()
--    and reorder shift queries (WHERE board_category='EPIC' AND team_id=? AND manual_order > ?)
DROP INDEX IF EXISTS idx_jira_issues_board_category_team;
CREATE INDEX idx_jira_issues_board_category_team ON jira_issues(board_category, team_id, manual_order);

-- 2. Composite (project_key, board_category) for findByProjectKeyAndBoardCategory()
--    Used by Board fast path to load PROJECT issues
CREATE INDEX idx_jira_issues_project_board_category ON jira_issues(project_key, board_category);
