-- Удаление deprecated колонки manual_priority_boost
-- Порядок эпиков теперь определяется через manual_order

ALTER TABLE jira_issues DROP COLUMN IF EXISTS manual_priority_boost;
