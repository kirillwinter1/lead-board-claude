-- V26: Workflow Configuration — clean DDL
-- Creates all workflow config tables.
-- No seed data: auto-detect from Jira populates on first sync.

-- ===================== 1. Config tables =====================

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

CREATE TABLE issue_type_mappings (
    id BIGSERIAL PRIMARY KEY,
    config_id BIGINT NOT NULL REFERENCES project_configurations(id) ON DELETE CASCADE,
    jira_type_name VARCHAR(200) NOT NULL,
    board_category VARCHAR(20) NOT NULL,
    workflow_role_code VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(config_id, jira_type_name)
);

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

CREATE TABLE link_type_mappings (
    id BIGSERIAL PRIMARY KEY,
    config_id BIGINT NOT NULL REFERENCES project_configurations(id) ON DELETE CASCADE,
    jira_link_type_name VARCHAR(200) NOT NULL,
    link_category VARCHAR(20) NOT NULL DEFAULT 'IGNORE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(config_id, jira_link_type_name)
);

CREATE TABLE tracker_metadata_cache (
    id BIGSERIAL PRIMARY KEY,
    cache_key VARCHAR(200) NOT NULL UNIQUE,
    data JSONB NOT NULL DEFAULT '{}',
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- System singleton (not Jira data — required for FK references)
INSERT INTO project_configurations (name, is_default)
VALUES ('Default', TRUE);

-- ===================== 2. teams: add project_config_id =====================

ALTER TABLE teams ADD COLUMN project_config_id BIGINT REFERENCES project_configurations(id);

-- ===================== 3. jira_issues: board_category + workflow_role =====================

ALTER TABLE jira_issues ADD COLUMN board_category VARCHAR(20);
ALTER TABLE jira_issues ADD COLUMN workflow_role VARCHAR(50);

-- Backfill board_category from is_subtask flag (fallback — auto-detect will overwrite)
UPDATE jira_issues SET board_category = 'SUBTASK' WHERE board_category IS NULL AND is_subtask = TRUE;
UPDATE jira_issues SET board_category = 'STORY'   WHERE board_category IS NULL AND is_subtask = FALSE;

-- ===================== 4. Indexes =====================

CREATE INDEX idx_jira_issues_board_category ON jira_issues(board_category);
CREATE INDEX idx_jira_issues_board_category_team ON jira_issues(board_category, team_id);
