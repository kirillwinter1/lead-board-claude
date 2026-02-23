-- T1: Initial tenant schema — all business tables
-- This migration runs in each tenant's schema (e.g., tenant_acme)
-- Tables here do NOT have schema qualifiers — they are created in the current schema

-- ============================================================
-- 1. project_configurations (no FK deps)
-- ============================================================
CREATE TABLE project_configurations (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL DEFAULT 'Default',
    is_default BOOLEAN NOT NULL DEFAULT TRUE,
    status_score_weights JSONB DEFAULT '{}',
    planning_allowed_categories VARCHAR(255) DEFAULT 'PLANNED,IN_PROGRESS',
    time_logging_allowed_categories VARCHAR(255) DEFAULT 'IN_PROGRESS',
    project_key VARCHAR(50),
    epic_link_type VARCHAR(20) DEFAULT 'parent',
    epic_link_name VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_project_configurations_project_key
    ON project_configurations(project_key) WHERE project_key IS NOT NULL;

INSERT INTO project_configurations (name, is_default) VALUES ('Default', TRUE);

-- ============================================================
-- 2. teams (FK to project_configurations)
-- ============================================================
CREATE TABLE teams (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    jira_team_value VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    atlassian_team_id VARCHAR(100),
    planning_config JSONB DEFAULT '{
        "gradeCoefficients": {"senior": 0.8, "middle": 1.0, "junior": 1.5},
        "riskBuffer": 0.2,
        "wipLimits": {"team": 6, "sa": 2, "dev": 3, "qa": 2}
    }'::jsonb,
    project_config_id BIGINT REFERENCES project_configurations(id),
    color VARCHAR(7),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_teams_atlassian_team_id ON teams(atlassian_team_id);

-- ============================================================
-- 3. team_members (FK to teams)
-- ============================================================
CREATE TABLE team_members (
    id BIGSERIAL PRIMARY KEY,
    team_id BIGINT NOT NULL REFERENCES teams(id),
    jira_account_id VARCHAR(255) NOT NULL,
    display_name VARCHAR(255),
    role VARCHAR(50) NOT NULL DEFAULT 'DEV',
    grade VARCHAR(50) NOT NULL DEFAULT 'MIDDLE',
    hours_per_day DECIMAL(3,1) NOT NULL DEFAULT 6.0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    avatar_url VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_hours_per_day CHECK (hours_per_day > 0 AND hours_per_day <= 24),
    CONSTRAINT chk_grade CHECK (grade IN ('JUNIOR', 'MIDDLE', 'SENIOR'))
);

CREATE INDEX idx_team_members_team_id ON team_members(team_id);
CREATE INDEX idx_team_members_jira_account_id ON team_members(jira_account_id);
CREATE UNIQUE INDEX idx_team_members_unique_account_per_team
    ON team_members(team_id, jira_account_id) WHERE active = TRUE;

-- ============================================================
-- 4. jira_issues (FK to teams)
-- ============================================================
CREATE TABLE jira_issues (
    id BIGSERIAL PRIMARY KEY,
    issue_key VARCHAR(50) NOT NULL UNIQUE,
    issue_id VARCHAR(50) NOT NULL,
    project_key VARCHAR(50) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    status VARCHAR(100) NOT NULL,
    issue_type VARCHAR(100) NOT NULL,
    is_subtask BOOLEAN NOT NULL DEFAULT FALSE,
    parent_key VARCHAR(50),
    original_estimate_seconds BIGINT,
    time_spent_seconds BIGINT,
    remaining_estimate_seconds BIGINT,
    jira_updated_at TIMESTAMP WITH TIME ZONE,
    jira_created_at TIMESTAMP WITH TIME ZONE,
    team_field_value VARCHAR(255),
    team_id BIGINT REFERENCES teams(id),
    rough_estimates JSONB,
    rough_estimate_updated_at TIMESTAMP WITH TIME ZONE,
    rough_estimate_updated_by VARCHAR(255),
    priority VARCHAR(50),
    due_date DATE,
    auto_score DECIMAL(5, 2),
    auto_score_calculated_at TIMESTAMP WITH TIME ZONE,
    flagged BOOLEAN DEFAULT FALSE,
    blocks TEXT[],
    is_blocked_by TEXT[],
    assignee_account_id VARCHAR(255),
    assignee_display_name VARCHAR(255),
    assignee_avatar_url VARCHAR(500),
    started_at TIMESTAMP WITH TIME ZONE,
    done_at TIMESTAMP WITH TIME ZONE,
    manual_order INTEGER,
    board_category VARCHAR(20),
    workflow_role VARCHAR(50),
    components TEXT[],
    child_epic_keys TEXT[],
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_jira_issues_project_key ON jira_issues(project_key);
CREATE INDEX idx_jira_issues_issue_type ON jira_issues(issue_type);
CREATE INDEX idx_jira_issues_parent_key ON jira_issues(parent_key);
CREATE INDEX idx_jira_issues_status ON jira_issues(status);
CREATE INDEX idx_jira_issues_team_id ON jira_issues(team_id);
CREATE INDEX idx_jira_issues_auto_score ON jira_issues(auto_score DESC NULLS LAST);
CREATE INDEX idx_jira_issues_due_date ON jira_issues(due_date);
CREATE INDEX idx_jira_issues_priority ON jira_issues(priority);
CREATE INDEX idx_jira_issues_blocks ON jira_issues USING GIN(blocks);
CREATE INDEX idx_jira_issues_is_blocked_by ON jira_issues USING GIN(is_blocked_by);
CREATE INDEX idx_jira_issues_flagged ON jira_issues(flagged) WHERE flagged = TRUE;
CREATE INDEX idx_jira_issues_assignee ON jira_issues(assignee_account_id) WHERE assignee_account_id IS NOT NULL;
CREATE INDEX idx_jira_issues_started_at ON jira_issues(started_at) WHERE started_at IS NOT NULL;
CREATE INDEX idx_jira_issues_done_at ON jira_issues(done_at);
CREATE INDEX idx_jira_issues_team_done ON jira_issues(team_id, done_at) WHERE done_at IS NOT NULL;
CREATE INDEX idx_jira_issues_assignee_done ON jira_issues(assignee_account_id, done_at) WHERE done_at IS NOT NULL AND assignee_account_id IS NOT NULL;
CREATE INDEX idx_jira_issues_team_manual_order ON jira_issues(team_id, manual_order);
CREATE INDEX idx_jira_issues_parent_manual_order ON jira_issues(parent_key, manual_order);
CREATE INDEX idx_jira_issues_board_category ON jira_issues(board_category);
CREATE INDEX idx_jira_issues_board_category_team ON jira_issues(board_category, team_id);
CREATE INDEX idx_jira_issues_components ON jira_issues USING GIN(components);

-- ============================================================
-- 5. jira_sync_state (no FK deps)
-- ============================================================
CREATE TABLE jira_sync_state (
    id BIGSERIAL PRIMARY KEY,
    project_key VARCHAR(50) NOT NULL UNIQUE,
    last_sync_started_at TIMESTAMP WITH TIME ZONE,
    last_sync_completed_at TIMESTAMP WITH TIME ZONE,
    last_sync_issues_count INTEGER DEFAULT 0,
    sync_in_progress BOOLEAN NOT NULL DEFAULT FALSE,
    last_error VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- ============================================================
-- 6. Workflow config tables (FK to project_configurations)
-- ============================================================
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
    board_category VARCHAR(20),
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
    color VARCHAR(20),
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

-- ============================================================
-- 7. tracker_metadata_cache (no FK deps)
-- ============================================================
CREATE TABLE tracker_metadata_cache (
    id BIGSERIAL PRIMARY KEY,
    cache_key VARCHAR(200) NOT NULL UNIQUE,
    data JSONB NOT NULL DEFAULT '{}',
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- 8. calendar_holidays (no FK deps)
-- ============================================================
CREATE TABLE calendar_holidays (
    id BIGSERIAL PRIMARY KEY,
    date DATE NOT NULL,
    name VARCHAR(255),
    country VARCHAR(2) NOT NULL DEFAULT 'RU',
    year INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    CONSTRAINT uq_calendar_holidays_date_country UNIQUE (date, country)
);

CREATE INDEX idx_calendar_holidays_date ON calendar_holidays(date);
CREATE INDEX idx_calendar_holidays_country_year ON calendar_holidays(country, year);

-- ============================================================
-- 9. status_changelog (FK to jira_issues)
-- ============================================================
CREATE TABLE status_changelog (
    id BIGSERIAL PRIMARY KEY,
    issue_key VARCHAR(50) NOT NULL,
    issue_id VARCHAR(50) NOT NULL,
    from_status VARCHAR(100),
    to_status VARCHAR(100) NOT NULL,
    transitioned_at TIMESTAMP WITH TIME ZONE NOT NULL,
    time_in_previous_status_seconds BIGINT,
    source VARCHAR(20) NOT NULL DEFAULT 'SYNC',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_changelog_issue FOREIGN KEY (issue_key)
        REFERENCES jira_issues(issue_key) ON DELETE CASCADE
);

CREATE INDEX idx_changelog_issue_key ON status_changelog(issue_key);
CREATE INDEX idx_changelog_transitioned_at ON status_changelog(transitioned_at);
CREATE INDEX idx_changelog_to_status ON status_changelog(to_status);
CREATE UNIQUE INDEX idx_changelog_unique ON status_changelog(issue_key, to_status, transitioned_at);

-- ============================================================
-- 10. flag_changelog
-- ============================================================
CREATE TABLE flag_changelog (
    id BIGSERIAL PRIMARY KEY,
    issue_key VARCHAR(50) NOT NULL,
    flagged_at TIMESTAMP WITH TIME ZONE NOT NULL,
    unflagged_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_flag_changelog_issue_key ON flag_changelog(issue_key);

-- ============================================================
-- 11. wip_snapshots (FK to teams)
-- ============================================================
CREATE TABLE wip_snapshots (
    id BIGSERIAL PRIMARY KEY,
    team_id BIGINT NOT NULL,
    snapshot_date DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    team_wip_limit INTEGER NOT NULL,
    team_wip_current INTEGER NOT NULL,
    role_wip_data JSONB,
    epics_in_queue INTEGER,
    total_epics INTEGER,
    CONSTRAINT fk_wip_snapshots_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
);

CREATE INDEX idx_wip_snapshots_team_date ON wip_snapshots(team_id, snapshot_date);
CREATE UNIQUE INDEX idx_wip_snapshots_unique ON wip_snapshots(team_id, snapshot_date);

-- ============================================================
-- 12. forecast_snapshots (FK to teams)
-- ============================================================
CREATE TABLE forecast_snapshots (
    id BIGSERIAL PRIMARY KEY,
    team_id BIGINT NOT NULL,
    snapshot_date DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    unified_planning_json JSONB NOT NULL,
    forecast_json JSONB NOT NULL,
    CONSTRAINT fk_forecast_snapshots_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
);

CREATE INDEX idx_forecast_snapshots_team_date ON forecast_snapshots(team_id, snapshot_date);
CREATE UNIQUE INDEX idx_forecast_snapshots_unique ON forecast_snapshots(team_id, snapshot_date);
CREATE INDEX idx_forecast_snapshots_planning_json ON forecast_snapshots USING GIN (unified_planning_json);

-- ============================================================
-- 13. Planning Poker tables
-- ============================================================
CREATE TABLE poker_sessions (
    id BIGSERIAL PRIMARY KEY,
    team_id BIGINT NOT NULL REFERENCES teams(id),
    epic_key VARCHAR(50) NOT NULL,
    facilitator_account_id VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PREPARING',
    room_code VARCHAR(10) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    CONSTRAINT chk_session_status CHECK (status IN ('PREPARING', 'ACTIVE', 'COMPLETED'))
);

CREATE INDEX idx_poker_sessions_room ON poker_sessions(room_code);
CREATE INDEX idx_poker_sessions_epic ON poker_sessions(epic_key);
CREATE INDEX idx_poker_sessions_team ON poker_sessions(team_id);

CREATE TABLE poker_stories (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES poker_sessions(id) ON DELETE CASCADE,
    story_key VARCHAR(50),
    title VARCHAR(500) NOT NULL,
    needs_roles JSONB DEFAULT '[]',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    final_estimates JSONB DEFAULT '{}',
    order_index INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_story_status CHECK (status IN ('PENDING', 'VOTING', 'REVEALED', 'COMPLETED'))
);

CREATE INDEX idx_poker_stories_session ON poker_stories(session_id);

CREATE TABLE poker_votes (
    id BIGSERIAL PRIMARY KEY,
    story_id BIGINT NOT NULL REFERENCES poker_stories(id) ON DELETE CASCADE,
    voter_account_id VARCHAR(100) NOT NULL,
    voter_display_name VARCHAR(255),
    voter_role VARCHAR(20) NOT NULL,
    vote_hours INTEGER,
    voted_at TIMESTAMPTZ,
    CONSTRAINT uq_vote_per_role UNIQUE(story_id, voter_account_id, voter_role)
);

CREATE INDEX idx_poker_votes_story ON poker_votes(story_id);

-- ============================================================
-- 14. simulation_logs (FK to teams)
-- ============================================================
CREATE TABLE simulation_logs (
    id BIGSERIAL PRIMARY KEY,
    team_id BIGINT NOT NULL REFERENCES teams(id),
    sim_date DATE NOT NULL,
    dry_run BOOLEAN NOT NULL DEFAULT false,
    actions JSONB NOT NULL DEFAULT '[]',
    summary JSONB NOT NULL DEFAULT '{}',
    status VARCHAR(50) NOT NULL DEFAULT 'RUNNING',
    error TEXT,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_sim_logs_team_date ON simulation_logs(team_id, sim_date DESC);

-- ============================================================
-- 15. RICE scoring tables
-- ============================================================
CREATE TABLE rice_templates (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    strategic_weight DECIMAL(3,2) DEFAULT 1.0,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE rice_criteria (
    id BIGSERIAL PRIMARY KEY,
    template_id BIGINT NOT NULL REFERENCES rice_templates(id),
    parameter VARCHAR(10) NOT NULL,
    name VARCHAR(300) NOT NULL,
    description TEXT,
    selection_type VARCHAR(20) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_rice_criteria_template ON rice_criteria(template_id);

CREATE TABLE rice_criteria_options (
    id BIGSERIAL PRIMARY KEY,
    criteria_id BIGINT NOT NULL REFERENCES rice_criteria(id) ON DELETE CASCADE,
    label VARCHAR(500) NOT NULL,
    description TEXT,
    score DECIMAL(10,2) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_rice_options_criteria ON rice_criteria_options(criteria_id);

CREATE TABLE rice_assessments (
    id BIGSERIAL PRIMARY KEY,
    issue_key VARCHAR(50) NOT NULL UNIQUE,
    template_id BIGINT NOT NULL REFERENCES rice_templates(id),
    assessed_by BIGINT,
    confidence DECIMAL(3,2),
    effort_manual VARCHAR(10),
    effort_auto DECIMAL(10,2),
    total_reach DECIMAL(10,2),
    total_impact DECIMAL(10,2),
    effective_effort DECIMAL(10,2),
    rice_score DECIMAL(10,2),
    normalized_score DECIMAL(5,2),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_rice_assessments_issue ON rice_assessments(issue_key);

CREATE TABLE rice_assessment_answers (
    id BIGSERIAL PRIMARY KEY,
    assessment_id BIGINT NOT NULL REFERENCES rice_assessments(id) ON DELETE CASCADE,
    criteria_id BIGINT NOT NULL REFERENCES rice_criteria(id),
    option_id BIGINT NOT NULL REFERENCES rice_criteria_options(id),
    score DECIMAL(10,2) NOT NULL
);

CREATE INDEX idx_rice_answers_assessment ON rice_assessment_answers(assessment_id);

-- ============================================================
-- 16. RICE seed data (Business + Technical templates)
-- ============================================================
INSERT INTO rice_templates (id, name, code, strategic_weight) VALUES (1, 'Business', 'business', 1.0);
INSERT INTO rice_templates (id, name, code, strategic_weight) VALUES (2, 'Technical', 'technical', 0.8);

-- Business: Reach
INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (1, 1, 'REACH', 'Тип фичи', 'Переиспользуемая или специфичная', 'SINGLE', 1);
INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(1, 'Продуктовая (переиспользуемая)', 3, 1), (1, 'Специфичная', 1, 2);

INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (2, 1, 'REACH', 'Кол-во пользователей', 'Сколько пользователей затронет', 'SINGLE', 2);
INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(2, '< 100', 1, 1), (2, '100-500', 3, 2), (2, '500-3000', 5, 3), (2, '3000+', 7, 4);

INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (3, 1, 'REACH', 'Кол-во клиентов/команд', 'Сколько клиентов или команд затронет', 'SINGLE', 3);
INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(3, '< 5', 1, 1), (3, '5-20', 2, 2), (3, '20-50', 3, 3), (3, '50+', 5, 4);

-- Business: Impact
INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (4, 1, 'IMPACT', 'Инициатор запроса', 'Кто инициировал запрос', 'SINGLE', 1);
INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(4, 'Внешний клиент', 3, 1), (4, 'Внутренний', 1, 2);

INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (5, 1, 'IMPACT', 'Тип задачи', 'Какой категории задача', 'MULTI', 2);
INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(5, 'Регуляторное требование', 5, 1), (5, 'Импортозамещение', 5, 2), (5, 'Развитие продукта', 1, 3), (5, 'Снижение рисков', 3, 4);

INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (6, 1, 'IMPACT', 'Соответствие целям', 'Совпадение с целями команды и компании', 'MULTI', 3);
INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(6, 'Цели команды', 1, 1), (6, 'Цели компании', 5, 2);

INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (7, 1, 'IMPACT', 'Экономия FTE (руб/мес)', 'Экономия в рублях в месяц', 'SINGLE', 4);
INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(7, 'Нет', 0, 1), (7, '< 100K', 1, 2), (7, '100-500K', 2, 3), (7, '500K-1M', 3, 4), (7, '1-3M', 4, 5), (7, '> 3M', 5, 6);

INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (8, 1, 'IMPACT', 'Финансовые потери при неисполнении', 'Потенциальные потери при невыполнении', 'SINGLE', 5);
INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(8, 'Нет', 0, 1), (8, '< 100K', 1, 2), (8, '100-500K', 2, 3), (8, '500K-1M', 3, 4), (8, '1-3M', 4, 5), (8, '> 3M', 5, 6);

INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (9, 1, 'IMPACT', 'Лояльность пользователей', 'Как влияет на лояльность', 'MULTI', 6);
INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(9, 'Удобство использования', 5, 1), (9, 'Ускорение процессов', 2, 2), (9, 'Сокращение ошибок', 2, 3);

INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (10, 1, 'IMPACT', 'Окупаемость', 'Срок окупаемости', 'SINGLE', 7);
INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(10, '> 1 года', 1, 1), (10, '< 1 года', 3, 2), (10, '< 6 мес', 5, 3);

-- Business: Confidence
INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (11, 1, 'CONFIDENCE', 'Уверенность в оценке', 'Насколько надёжны данные для оценки', 'SINGLE', 1);
INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(11, 'High — есть данные, метрики подтверждены', 1.0, 1), (11, 'Medium — есть данные, но не по всем критериям', 0.8, 2),
(11, 'Low — оценки предположительные', 0.6, 3), (11, 'Very Low — данные отсутствуют или неподтверждены', 0.4, 4);

-- Business: Effort
INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (12, 1, 'EFFORT', 'Размер (T-shirt)', 'Ориентировочный размер задачи.', 'SINGLE', 1);
INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(12, 'S (Small)', 1, 1), (12, 'M (Medium)', 2, 2), (12, 'L (Large)', 4, 3), (12, 'XL (Extra Large)', 8, 4);

-- Technical: Reach
INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (13, 2, 'REACH', 'Scope влияния', 'Масштаб затронутых систем', 'SINGLE', 1);
INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(13, 'Один сервис', 1, 1), (13, 'Несколько сервисов', 3, 2), (13, 'Вся система', 5, 3);

INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (14, 2, 'REACH', 'Частота проблемы', 'Как часто проявляется проблема', 'SINGLE', 2);
INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(14, 'Редко', 1, 1), (14, 'Еженедельно', 3, 2), (14, 'Ежедневно', 5, 3);

-- Technical: Impact
INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (15, 2, 'IMPACT', 'Влияние на стабильность', 'Как влияет на стабильность системы', 'SINGLE', 1);
INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(15, 'Низкое', 1, 1), (15, 'Среднее', 3, 2), (15, 'Высокое', 5, 3), (15, 'Критичное', 10, 4);

INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (16, 2, 'IMPACT', 'Влияние на производительность', 'Как влияет на перформанс', 'SINGLE', 2);
INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(16, 'Нет', 0, 1), (16, 'Небольшое', 2, 2), (16, 'Существенное', 5, 3);

INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (17, 2, 'IMPACT', 'Ускорение разработки', 'Экономия времени разработчиков', 'SINGLE', 3);
INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(17, 'Нет', 0, 1), (17, 'Немного', 2, 2), (17, 'Существенно', 5, 3);

INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (18, 2, 'IMPACT', 'Устранение техдолга', 'Уровень техдолга', 'SINGLE', 4);
INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(18, 'Косметический', 1, 1), (18, 'Архитектурный', 3, 2), (18, 'Критичный', 5, 3);

INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (19, 2, 'IMPACT', 'Риск безопасности', 'Влияние на безопасность', 'SINGLE', 5);
INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(19, 'Нет', 0, 1), (19, 'Низкий', 3, 2), (19, 'Высокий', 5, 3);

-- Technical: Confidence
INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (20, 2, 'CONFIDENCE', 'Уверенность в оценке', 'Насколько надёжны данные для оценки', 'SINGLE', 1);
INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(20, 'High — есть данные, метрики подтверждены', 1.0, 1), (20, 'Medium — есть данные, но не по всем критериям', 0.8, 2),
(20, 'Low — оценки предположительные', 0.6, 3), (20, 'Very Low — данные отсутствуют или неподтверждены', 0.4, 4);

-- Technical: Effort
INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (21, 2, 'EFFORT', 'Размер (T-shirt)', 'Ориентировочный размер задачи.', 'SINGLE', 1);
INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(21, 'S (Small)', 1, 1), (21, 'M (Medium)', 2, 2), (21, 'L (Large)', 4, 3), (21, 'XL (Extra Large)', 8, 4);

SELECT setval('rice_templates_id_seq', (SELECT MAX(id) FROM rice_templates));
SELECT setval('rice_criteria_id_seq', (SELECT MAX(id) FROM rice_criteria));
SELECT setval('rice_criteria_options_id_seq', (SELECT MAX(id) FROM rice_criteria_options));

-- ============================================================
-- 17. member_competencies (FK to team_members)
-- ============================================================
CREATE TABLE member_competencies (
    id BIGSERIAL PRIMARY KEY,
    team_member_id BIGINT NOT NULL REFERENCES team_members(id) ON DELETE CASCADE,
    component_name VARCHAR(200) NOT NULL,
    level INTEGER NOT NULL DEFAULT 3,
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(team_member_id, component_name),
    CHECK(level >= 1 AND level <= 5)
);

CREATE INDEX idx_member_competencies_member ON member_competencies(team_member_id);

-- ============================================================
-- 18. member_absences (FK to team_members)
-- ============================================================
CREATE TABLE member_absences (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL REFERENCES team_members(id) ON DELETE CASCADE,
    absence_type VARCHAR(20) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    comment VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_absence_dates CHECK (end_date >= start_date),
    CONSTRAINT chk_absence_type CHECK (absence_type IN ('VACATION','SICK_LEAVE','DAY_OFF','OTHER'))
);

CREATE INDEX idx_member_absences_member ON member_absences(member_id);
CREATE INDEX idx_member_absences_dates ON member_absences(member_id, start_date, end_date);

-- ============================================================
-- 19. bug_sla_config (no FK deps)
-- ============================================================
CREATE TABLE bug_sla_config (
    id BIGSERIAL PRIMARY KEY,
    priority VARCHAR(50) NOT NULL UNIQUE,
    max_resolution_hours INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO bug_sla_config (priority, max_resolution_hours) VALUES
    ('Highest', 24), ('High', 72), ('Medium', 168), ('Low', 336), ('Lowest', 672);
