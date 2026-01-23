-- Добавляем поля для AutoScore

-- Поля из Jira для расчёта приоритета
ALTER TABLE jira_issues ADD COLUMN priority VARCHAR(50);
ALTER TABLE jira_issues ADD COLUMN due_date DATE;
ALTER TABLE jira_issues ADD COLUMN jira_created_at TIMESTAMP WITH TIME ZONE;

-- Ручной boost приоритета (локальные данные Lead Board)
ALTER TABLE jira_issues ADD COLUMN manual_priority_boost INTEGER DEFAULT 0;

-- Кэш AutoScore для производительности
ALTER TABLE jira_issues ADD COLUMN auto_score DECIMAL(5, 2);
ALTER TABLE jira_issues ADD COLUMN auto_score_calculated_at TIMESTAMP WITH TIME ZONE;

-- Индексы для сортировки по AutoScore
CREATE INDEX idx_jira_issues_auto_score ON jira_issues(auto_score DESC NULLS LAST);
CREATE INDEX idx_jira_issues_due_date ON jira_issues(due_date);
CREATE INDEX idx_jira_issues_priority ON jira_issues(priority);

COMMENT ON COLUMN jira_issues.priority IS 'Приоритет из Jira: Highest, High, Medium, Low, Lowest';
COMMENT ON COLUMN jira_issues.due_date IS 'Срок выполнения из Jira';
COMMENT ON COLUMN jira_issues.jira_created_at IS 'Дата создания задачи в Jira';
COMMENT ON COLUMN jira_issues.manual_priority_boost IS 'Ручная корректировка приоритета (0-5)';
COMMENT ON COLUMN jira_issues.auto_score IS 'Рассчитанный AutoScore (0-100)';
COMMENT ON COLUMN jira_issues.auto_score_calculated_at IS 'Время последнего расчёта AutoScore';
