-- V26: Workflow Configuration tables
-- Moves hardcoded roles, issue type mappings, status mappings, and link type mappings to DB

-- 1. Project configurations
CREATE TABLE project_configurations (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL DEFAULT 'Default',
    is_default BOOLEAN NOT NULL DEFAULT TRUE,
    status_score_weights JSONB DEFAULT '{}',
    planning_allowed_categories VARCHAR(255) DEFAULT 'PLANNED,IN_PROGRESS',
    time_logging_allowed_categories VARCHAR(255) DEFAULT 'IN_PROGRESS',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. Workflow roles (dynamic pipeline)
CREATE TABLE workflow_roles (
    id BIGSERIAL PRIMARY KEY,
    config_id BIGINT NOT NULL REFERENCES project_configurations(id) ON DELETE CASCADE,
    code VARCHAR(50) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    color VARCHAR(20) DEFAULT '#666666',
    sort_order INT NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(config_id, code),
    UNIQUE(config_id, sort_order)
);

-- 3. Issue type mappings (Jira type name → board category)
CREATE TABLE issue_type_mappings (
    id BIGSERIAL PRIMARY KEY,
    config_id BIGINT NOT NULL REFERENCES project_configurations(id) ON DELETE CASCADE,
    jira_type_name VARCHAR(200) NOT NULL,
    board_category VARCHAR(20) NOT NULL,
    workflow_role_code VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(config_id, jira_type_name)
);

-- 4. Status mappings (Jira status → category per issue category)
CREATE TABLE status_mappings (
    id BIGSERIAL PRIMARY KEY,
    config_id BIGINT NOT NULL REFERENCES project_configurations(id) ON DELETE CASCADE,
    jira_status_name VARCHAR(200) NOT NULL,
    issue_category VARCHAR(20) NOT NULL,
    status_category VARCHAR(20) NOT NULL,
    workflow_role_code VARCHAR(50),
    sort_order INT DEFAULT 0,
    score_weight INT DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(config_id, jira_status_name, issue_category)
);

-- 5. Link type mappings
CREATE TABLE link_type_mappings (
    id BIGSERIAL PRIMARY KEY,
    config_id BIGINT NOT NULL REFERENCES project_configurations(id) ON DELETE CASCADE,
    jira_link_type_name VARCHAR(200) NOT NULL,
    link_category VARCHAR(20) NOT NULL DEFAULT 'IGNORE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(config_id, jira_link_type_name)
);

-- 6. Tracker metadata cache
CREATE TABLE tracker_metadata_cache (
    id BIGSERIAL PRIMARY KEY,
    cache_key VARCHAR(200) NOT NULL UNIQUE,
    data JSONB NOT NULL DEFAULT '{}',
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 7. Add project_config_id to teams
ALTER TABLE teams ADD COLUMN project_config_id BIGINT REFERENCES project_configurations(id);

-- 8. Add board_category and workflow_role to jira_issues
ALTER TABLE jira_issues ADD COLUMN board_category VARCHAR(20);
ALTER TABLE jira_issues ADD COLUMN workflow_role VARCHAR(50);

-- 9. Add rough_estimates JSONB column (replaces sa/dev/qa columns)
ALTER TABLE jira_issues ADD COLUMN rough_estimates JSONB;

-- 10. Migrate existing rough estimate data to JSONB
UPDATE jira_issues
SET rough_estimates = jsonb_build_object(
    'SA', COALESCE(rough_estimate_sa_days, 0),
    'DEV', COALESCE(rough_estimate_dev_days, 0),
    'QA', COALESCE(rough_estimate_qa_days, 0)
)
WHERE rough_estimate_sa_days IS NOT NULL
   OR rough_estimate_dev_days IS NOT NULL
   OR rough_estimate_qa_days IS NOT NULL;

-- 11. Drop old rough estimate columns
ALTER TABLE jira_issues DROP COLUMN rough_estimate_sa_days;
ALTER TABLE jira_issues DROP COLUMN rough_estimate_dev_days;
ALTER TABLE jira_issues DROP COLUMN rough_estimate_qa_days;

-- ========== SEED DATA ==========

-- Default project configuration
INSERT INTO project_configurations (name, is_default, status_score_weights, planning_allowed_categories, time_logging_allowed_categories)
VALUES ('Default', TRUE,
    '{
        "Acceptance": 30, "Приёмка": 30, "Приемка": 30,
        "E2E Testing": 30, "E2E Тестирование": 30, "e2e testing": 30,
        "Developing": 25, "В разработке": 25, "Development": 25, "In Progress": 25, "В работе": 25, "В Разработке": 25,
        "Planned": 15, "Запланировано": 15, "Ready": 15,
        "Rough Estimate": 10, "Estimation": 10, "Оценка": 10, "Estimate": 10,
        "Requirements": 5, "Требования": 5, "Analysis": 5, "Аналитика": 5,
        "New": -5, "Новое": -5, "Новый": -5, "Backlog": -5, "To Do": -5, "Open": -5
    }'::jsonb,
    'PLANNED,IN_PROGRESS',
    'IN_PROGRESS'
);

-- Default workflow roles: SA → DEV → QA
INSERT INTO workflow_roles (config_id, code, display_name, color, sort_order, is_default)
VALUES
    (1, 'SA',  'System Analyst', '#3b82f6', 1, FALSE),
    (1, 'DEV', 'Developer',      '#10b981', 2, TRUE),
    (1, 'QA',  'QA Engineer',    '#f59e0b', 3, FALSE);

-- Issue type mappings
INSERT INTO issue_type_mappings (config_id, jira_type_name, board_category, workflow_role_code) VALUES
    -- Epics
    (1, 'Epic',          'EPIC',    NULL),
    (1, 'Эпик',         'EPIC',    NULL),
    -- Stories
    (1, 'Story',         'STORY',   NULL),
    (1, 'История',       'STORY',   NULL),
    (1, 'Bug',           'STORY',   NULL),
    (1, 'Баг',           'STORY',   NULL),
    (1, 'Task',          'STORY',   NULL),
    (1, 'Задача',        'STORY',   NULL),
    -- Subtasks with role mapping
    (1, 'Sub-task',      'SUBTASK', 'DEV'),
    (1, 'Подзадача',     'SUBTASK', 'DEV'),
    (1, 'Аналитика',     'SUBTASK', 'SA'),
    (1, 'Analytics',     'SUBTASK', 'SA'),
    (1, 'Разработка',    'SUBTASK', 'DEV'),
    (1, 'Development',   'SUBTASK', 'DEV'),
    (1, 'Тестирование',  'SUBTASK', 'QA'),
    (1, 'Testing',       'SUBTASK', 'QA'),
    -- Ignored types
    (1, 'Documentation', 'IGNORE',  NULL),
    (1, 'Meta-task',     'IGNORE',  NULL);

-- Status mappings for EPIC
INSERT INTO status_mappings (config_id, jira_status_name, issue_category, status_category, workflow_role_code, sort_order, score_weight) VALUES
    -- EPIC: NEW
    (1, 'New',              'EPIC', 'NEW',          NULL, 0,  -5),
    (1, 'Новый',            'EPIC', 'NEW',          NULL, 0,  -5),
    -- EPIC: REQUIREMENTS
    (1, 'Requirements',     'EPIC', 'REQUIREMENTS', NULL, 10, 5),
    (1, 'Требования',       'EPIC', 'REQUIREMENTS', NULL, 10, 5),
    (1, 'Rough Estimate',   'EPIC', 'REQUIREMENTS', NULL, 15, 10),
    (1, 'Оценка',           'EPIC', 'REQUIREMENTS', NULL, 15, 10),
    (1, 'Backlog',          'EPIC', 'REQUIREMENTS', NULL, 5,  -5),
    (1, 'Бэклог',           'EPIC', 'REQUIREMENTS', NULL, 5,  -5),
    (1, 'To Do',            'EPIC', 'REQUIREMENTS', NULL, 8,  -5),
    (1, 'Сделать',          'EPIC', 'REQUIREMENTS', NULL, 8,  -5),
    -- EPIC: PLANNED
    (1, 'Planned',          'EPIC', 'PLANNED',      NULL, 20, 15),
    (1, 'Запланировано',    'EPIC', 'PLANNED',      NULL, 20, 15),
    -- EPIC: IN_PROGRESS
    (1, 'Developing',       'EPIC', 'IN_PROGRESS',  NULL, 30, 25),
    (1, 'В разработке',     'EPIC', 'IN_PROGRESS',  NULL, 30, 25),
    (1, 'E2E Testing',      'EPIC', 'IN_PROGRESS',  NULL, 35, 30),
    (1, 'E2E Тестирование', 'EPIC', 'IN_PROGRESS',  NULL, 35, 30),
    (1, 'Acceptance',       'EPIC', 'IN_PROGRESS',  NULL, 38, 30),
    (1, 'Приёмка',          'EPIC', 'IN_PROGRESS',  NULL, 38, 30),
    (1, 'Приемка',          'EPIC', 'IN_PROGRESS',  NULL, 38, 30),
    -- EPIC: DONE
    (1, 'Done',             'EPIC', 'DONE',         NULL, 50, 0),
    (1, 'Готово',           'EPIC', 'DONE',         NULL, 50, 0),
    (1, 'Closed',           'EPIC', 'DONE',         NULL, 50, 0),
    (1, 'Закрыто',          'EPIC', 'DONE',         NULL, 50, 0),
    (1, 'Resolved',         'EPIC', 'DONE',         NULL, 50, 0),
    (1, 'Решено',           'EPIC', 'DONE',         NULL, 50, 0);

-- Status mappings for STORY
INSERT INTO status_mappings (config_id, jira_status_name, issue_category, status_category, workflow_role_code, sort_order, score_weight) VALUES
    -- STORY: NEW
    (1, 'New',              'STORY', 'NEW',         NULL,  0, 0),
    (1, 'Новый',            'STORY', 'NEW',         NULL,  0, 0),
    (1, 'Ready',            'STORY', 'NEW',         NULL,  2, 0),
    (1, 'Готов',            'STORY', 'NEW',         NULL,  2, 0),
    (1, 'Waiting Dev',      'STORY', 'NEW',         NULL,  3, 0),
    (1, 'Ожидает разработки','STORY','NEW',         NULL,  3, 0),
    (1, 'Waiting QA',       'STORY', 'NEW',         NULL,  4, 0),
    (1, 'Ожидает тестирования','STORY','NEW',       NULL,  4, 0),
    (1, 'Ready to Release', 'STORY', 'NEW',         NULL,  5, 0),
    (1, 'Готов к релизу',   'STORY', 'NEW',         NULL,  5, 0),
    -- STORY: IN_PROGRESS
    (1, 'Analysis',         'STORY', 'IN_PROGRESS', 'SA',  10, 0),
    (1, 'Анализ',           'STORY', 'IN_PROGRESS', 'SA',  10, 0),
    (1, 'Analysis Review',  'STORY', 'IN_PROGRESS', 'SA',  12, 0),
    (1, 'Ревью анализа',    'STORY', 'IN_PROGRESS', 'SA',  12, 0),
    (1, 'Development',      'STORY', 'IN_PROGRESS', 'DEV', 20, 0),
    (1, 'Разработка',       'STORY', 'IN_PROGRESS', 'DEV', 20, 0),
    (1, 'Dev Review',       'STORY', 'IN_PROGRESS', 'DEV', 22, 0),
    (1, 'Ревью разработки', 'STORY', 'IN_PROGRESS', 'DEV', 22, 0),
    (1, 'Testing',          'STORY', 'IN_PROGRESS', 'QA',  30, 0),
    (1, 'Тестирование',     'STORY', 'IN_PROGRESS', 'QA',  30, 0),
    (1, 'Test Review',      'STORY', 'IN_PROGRESS', 'QA',  32, 0),
    (1, 'Ревью тестирования','STORY','IN_PROGRESS', 'QA',  32, 0),
    -- STORY: DONE
    (1, 'Done',             'STORY', 'DONE',        NULL,  50, 0),
    (1, 'Готово',           'STORY', 'DONE',        NULL,  50, 0);

-- Status mappings for SUBTASK
INSERT INTO status_mappings (config_id, jira_status_name, issue_category, status_category, workflow_role_code, sort_order, score_weight) VALUES
    -- SUBTASK: NEW
    (1, 'New',              'SUBTASK', 'NEW',         NULL, 0, 0),
    (1, 'Новый',            'SUBTASK', 'NEW',         NULL, 0, 0),
    -- SUBTASK: IN_PROGRESS
    (1, 'In Progress',      'SUBTASK', 'IN_PROGRESS', NULL, 10, 0),
    (1, 'В работе',         'SUBTASK', 'IN_PROGRESS', NULL, 10, 0),
    (1, 'Review',           'SUBTASK', 'IN_PROGRESS', NULL, 15, 0),
    (1, 'Ревью',            'SUBTASK', 'IN_PROGRESS', NULL, 15, 0),
    -- SUBTASK: DONE
    (1, 'Done',             'SUBTASK', 'DONE',        NULL, 50, 0),
    (1, 'Готово',           'SUBTASK', 'DONE',        NULL, 50, 0);

-- Link type mappings
INSERT INTO link_type_mappings (config_id, jira_link_type_name, link_category) VALUES
    (1, 'Blocks',    'BLOCKS'),
    (1, 'Duplicate', 'IGNORE'),
    (1, 'Cloners',   'IGNORE'),
    (1, 'Relates',   'RELATED');

-- Create index for board_category queries
CREATE INDEX idx_jira_issues_board_category ON jira_issues(board_category);
CREATE INDEX idx_jira_issues_board_category_team ON jira_issues(board_category, team_id);
