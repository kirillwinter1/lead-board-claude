-- Добавляем поля для Story AutoScore (F19)

-- Флаг "Impediment" из Jira
ALTER TABLE jira_issues ADD COLUMN flagged BOOLEAN DEFAULT FALSE;

-- Зависимости между задачами (Issue Links)
ALTER TABLE jira_issues ADD COLUMN blocks TEXT[];
ALTER TABLE jira_issues ADD COLUMN is_blocked_by TEXT[];

-- Индексы для поиска зависимостей
CREATE INDEX idx_jira_issues_blocks ON jira_issues USING GIN(blocks);
CREATE INDEX idx_jira_issues_is_blocked_by ON jira_issues USING GIN(is_blocked_by);
CREATE INDEX idx_jira_issues_flagged ON jira_issues(flagged) WHERE flagged = TRUE;

COMMENT ON COLUMN jira_issues.flagged IS 'Флаг Impediment из Jira - работа приостановлена';
COMMENT ON COLUMN jira_issues.blocks IS 'Массив issue keys которые блокируются этой задачей';
COMMENT ON COLUMN jira_issues.is_blocked_by IS 'Массив issue keys которые блокируют эту задачу';
