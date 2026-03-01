-- 02_tenant_schemas.sql: Create perf tenant schemas with all business tables
-- Replicates T1+T2+T3+T4 migrations for each perf tenant

DO $$
DECLARE
    schema_names TEXT[] := ARRAY['tenant_perf_alpha', 'tenant_perf_beta', 'tenant_perf_gamma'];
    s TEXT;
BEGIN
    FOREACH s IN ARRAY schema_names LOOP
        EXECUTE format('CREATE SCHEMA IF NOT EXISTS %I', s);
        RAISE NOTICE 'Created schema: %', s;
    END LOOP;
END $$;

-- Function to bootstrap a tenant schema (T1+T2+T3+T4 combined)
CREATE OR REPLACE FUNCTION perf_bootstrap_tenant_schema(schema_name TEXT) RETURNS VOID AS $$
BEGIN
    -- Set search_path to the tenant schema
    EXECUTE format('SET search_path TO %I', schema_name);

    -- T1: project_configurations
    EXECUTE 'CREATE TABLE IF NOT EXISTS project_configurations (
        id BIGSERIAL PRIMARY KEY,
        name VARCHAR(100) NOT NULL DEFAULT ''Default'',
        is_default BOOLEAN NOT NULL DEFAULT TRUE,
        status_score_weights JSONB DEFAULT ''{}'',
        planning_allowed_categories VARCHAR(255) DEFAULT ''PLANNED,IN_PROGRESS'',
        time_logging_allowed_categories VARCHAR(255) DEFAULT ''IN_PROGRESS'',
        project_key VARCHAR(50),
        epic_link_type VARCHAR(20) DEFAULT ''parent'',
        epic_link_name VARCHAR(100),
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    )';
    EXECUTE 'CREATE UNIQUE INDEX IF NOT EXISTS idx_project_configurations_project_key
        ON project_configurations(project_key) WHERE project_key IS NOT NULL';
    EXECUTE 'INSERT INTO project_configurations (name, is_default) VALUES (''Default'', TRUE) ON CONFLICT DO NOTHING';

    -- T1: teams
    EXECUTE 'CREATE TABLE IF NOT EXISTS teams (
        id BIGSERIAL PRIMARY KEY,
        name VARCHAR(255) NOT NULL,
        jira_team_value VARCHAR(255),
        active BOOLEAN NOT NULL DEFAULT TRUE,
        atlassian_team_id VARCHAR(100),
        planning_config JSONB DEFAULT ''{
            "gradeCoefficients": {"senior": 0.8, "middle": 1.0, "junior": 1.5},
            "riskBuffer": 0.2,
            "wipLimits": {"team": 6, "sa": 2, "dev": 3, "qa": 2}
        }''::jsonb,
        project_config_id BIGINT REFERENCES project_configurations(id),
        color VARCHAR(7),
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    )';

    -- T1: team_members
    EXECUTE 'CREATE TABLE IF NOT EXISTS team_members (
        id BIGSERIAL PRIMARY KEY,
        team_id BIGINT NOT NULL REFERENCES teams(id),
        jira_account_id VARCHAR(255) NOT NULL,
        display_name VARCHAR(255),
        role VARCHAR(50) NOT NULL DEFAULT ''DEV'',
        grade VARCHAR(50) NOT NULL DEFAULT ''MIDDLE'',
        hours_per_day DECIMAL(3,1) NOT NULL DEFAULT 6.0,
        active BOOLEAN NOT NULL DEFAULT TRUE,
        avatar_url VARCHAR(500),
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT chk_hours_per_day CHECK (hours_per_day > 0 AND hours_per_day <= 24),
        CONSTRAINT chk_grade CHECK (grade IN (''JUNIOR'', ''MIDDLE'', ''SENIOR''))
    )';

    -- T1: jira_issues
    EXECUTE 'CREATE TABLE IF NOT EXISTS jira_issues (
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
    )';
    EXECUTE 'CREATE INDEX IF NOT EXISTS idx_jira_issues_project_key ON jira_issues(project_key)';
    EXECUTE 'CREATE INDEX IF NOT EXISTS idx_jira_issues_issue_type ON jira_issues(issue_type)';
    EXECUTE 'CREATE INDEX IF NOT EXISTS idx_jira_issues_parent_key ON jira_issues(parent_key)';
    EXECUTE 'CREATE INDEX IF NOT EXISTS idx_jira_issues_status ON jira_issues(status)';
    EXECUTE 'CREATE INDEX IF NOT EXISTS idx_jira_issues_team_id ON jira_issues(team_id)';
    EXECUTE 'CREATE INDEX IF NOT EXISTS idx_jira_issues_board_category ON jira_issues(board_category)';
    EXECUTE 'CREATE INDEX IF NOT EXISTS idx_jira_issues_board_category_team ON jira_issues(board_category, team_id)';
    EXECUTE 'CREATE INDEX IF NOT EXISTS idx_jira_issues_auto_score ON jira_issues(auto_score DESC NULLS LAST)';
    EXECUTE 'CREATE INDEX IF NOT EXISTS idx_jira_issues_done_at ON jira_issues(done_at)';

    -- T1: jira_sync_state
    EXECUTE 'CREATE TABLE IF NOT EXISTS jira_sync_state (
        id BIGSERIAL PRIMARY KEY,
        project_key VARCHAR(50) NOT NULL UNIQUE,
        last_sync_started_at TIMESTAMP WITH TIME ZONE,
        last_sync_completed_at TIMESTAMP WITH TIME ZONE,
        last_sync_issues_count INTEGER DEFAULT 0,
        sync_in_progress BOOLEAN NOT NULL DEFAULT FALSE,
        last_error VARCHAR(1000),
        created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
    )';

    -- T1: workflow config tables
    EXECUTE 'CREATE TABLE IF NOT EXISTS workflow_roles (
        id BIGSERIAL PRIMARY KEY,
        config_id BIGINT NOT NULL REFERENCES project_configurations(id) ON DELETE CASCADE,
        code VARCHAR(50) NOT NULL,
        display_name VARCHAR(100) NOT NULL,
        color VARCHAR(20) DEFAULT ''#666666'',
        sort_order INT NOT NULL,
        is_default BOOLEAN NOT NULL DEFAULT FALSE,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        UNIQUE(config_id, code),
        UNIQUE(config_id, sort_order)
    )';
    EXECUTE 'CREATE TABLE IF NOT EXISTS issue_type_mappings (
        id BIGSERIAL PRIMARY KEY,
        config_id BIGINT NOT NULL REFERENCES project_configurations(id) ON DELETE CASCADE,
        jira_type_name VARCHAR(200) NOT NULL,
        board_category VARCHAR(20),
        workflow_role_code VARCHAR(50),
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        UNIQUE(config_id, jira_type_name)
    )';
    EXECUTE 'CREATE TABLE IF NOT EXISTS status_mappings (
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
    )';
    EXECUTE 'CREATE TABLE IF NOT EXISTS link_type_mappings (
        id BIGSERIAL PRIMARY KEY,
        config_id BIGINT NOT NULL REFERENCES project_configurations(id) ON DELETE CASCADE,
        jira_link_type_name VARCHAR(200) NOT NULL,
        link_category VARCHAR(20) NOT NULL DEFAULT ''IGNORE'',
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        UNIQUE(config_id, jira_link_type_name)
    )';

    -- T1: tracker_metadata_cache
    EXECUTE 'CREATE TABLE IF NOT EXISTS tracker_metadata_cache (
        id BIGSERIAL PRIMARY KEY,
        cache_key VARCHAR(200) NOT NULL UNIQUE,
        data JSONB NOT NULL DEFAULT ''{}'',
        fetched_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    )';

    -- T1: calendar_holidays
    EXECUTE 'CREATE TABLE IF NOT EXISTS calendar_holidays (
        id BIGSERIAL PRIMARY KEY,
        date DATE NOT NULL,
        name VARCHAR(255),
        country VARCHAR(2) NOT NULL DEFAULT ''RU'',
        year INTEGER NOT NULL,
        created_at TIMESTAMP DEFAULT NOW(),
        CONSTRAINT uq_calendar_holidays_date_country UNIQUE (date, country)
    )';

    -- T1: status_changelog
    EXECUTE 'CREATE TABLE IF NOT EXISTS status_changelog (
        id BIGSERIAL PRIMARY KEY,
        issue_key VARCHAR(50) NOT NULL,
        issue_id VARCHAR(50) NOT NULL,
        from_status VARCHAR(100),
        to_status VARCHAR(100) NOT NULL,
        transitioned_at TIMESTAMP WITH TIME ZONE NOT NULL,
        time_in_previous_status_seconds BIGINT,
        source VARCHAR(20) NOT NULL DEFAULT ''SYNC'',
        created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
        CONSTRAINT fk_changelog_issue FOREIGN KEY (issue_key)
            REFERENCES jira_issues(issue_key) ON DELETE CASCADE
    )';
    EXECUTE 'CREATE INDEX IF NOT EXISTS idx_changelog_issue_key ON status_changelog(issue_key)';
    EXECUTE 'CREATE INDEX IF NOT EXISTS idx_changelog_transitioned_at ON status_changelog(transitioned_at)';

    -- T1: flag_changelog
    EXECUTE 'CREATE TABLE IF NOT EXISTS flag_changelog (
        id BIGSERIAL PRIMARY KEY,
        issue_key VARCHAR(50) NOT NULL,
        flagged_at TIMESTAMP WITH TIME ZONE NOT NULL,
        unflagged_at TIMESTAMP WITH TIME ZONE,
        created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
    )';

    -- T1: wip_snapshots
    EXECUTE 'CREATE TABLE IF NOT EXISTS wip_snapshots (
        id BIGSERIAL PRIMARY KEY,
        team_id BIGINT NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
        snapshot_date DATE NOT NULL,
        created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
        team_wip_limit INTEGER NOT NULL,
        team_wip_current INTEGER NOT NULL,
        role_wip_data JSONB,
        epics_in_queue INTEGER,
        total_epics INTEGER,
        UNIQUE(team_id, snapshot_date)
    )';

    -- T1: forecast_snapshots
    EXECUTE 'CREATE TABLE IF NOT EXISTS forecast_snapshots (
        id BIGSERIAL PRIMARY KEY,
        team_id BIGINT NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
        snapshot_date DATE NOT NULL,
        created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
        unified_planning_json JSONB NOT NULL,
        forecast_json JSONB NOT NULL,
        UNIQUE(team_id, snapshot_date)
    )';

    -- T1: poker tables
    EXECUTE 'CREATE TABLE IF NOT EXISTS poker_sessions (
        id BIGSERIAL PRIMARY KEY,
        team_id BIGINT NOT NULL REFERENCES teams(id),
        epic_key VARCHAR(50) NOT NULL,
        facilitator_account_id VARCHAR(100) NOT NULL,
        status VARCHAR(20) NOT NULL DEFAULT ''PREPARING'',
        room_code VARCHAR(10) NOT NULL UNIQUE,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        started_at TIMESTAMPTZ,
        completed_at TIMESTAMPTZ,
        CONSTRAINT chk_session_status CHECK (status IN (''PREPARING'', ''ACTIVE'', ''COMPLETED''))
    )';
    EXECUTE 'CREATE TABLE IF NOT EXISTS poker_stories (
        id BIGSERIAL PRIMARY KEY,
        session_id BIGINT NOT NULL REFERENCES poker_sessions(id) ON DELETE CASCADE,
        story_key VARCHAR(50),
        title VARCHAR(500) NOT NULL,
        needs_roles JSONB DEFAULT ''[]'',
        status VARCHAR(20) NOT NULL DEFAULT ''PENDING'',
        final_estimates JSONB DEFAULT ''{}'',
        order_index INTEGER NOT NULL,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        CONSTRAINT chk_story_status CHECK (status IN (''PENDING'', ''VOTING'', ''REVEALED'', ''COMPLETED''))
    )';
    EXECUTE 'CREATE TABLE IF NOT EXISTS poker_votes (
        id BIGSERIAL PRIMARY KEY,
        story_id BIGINT NOT NULL REFERENCES poker_stories(id) ON DELETE CASCADE,
        voter_account_id VARCHAR(100) NOT NULL,
        voter_display_name VARCHAR(255),
        voter_role VARCHAR(20) NOT NULL,
        vote_hours INTEGER,
        voted_at TIMESTAMPTZ,
        UNIQUE(story_id, voter_account_id, voter_role)
    )';

    -- T1: simulation_logs
    EXECUTE 'CREATE TABLE IF NOT EXISTS simulation_logs (
        id BIGSERIAL PRIMARY KEY,
        team_id BIGINT NOT NULL REFERENCES teams(id),
        sim_date DATE NOT NULL,
        dry_run BOOLEAN NOT NULL DEFAULT false,
        actions JSONB NOT NULL DEFAULT ''[]'',
        summary JSONB NOT NULL DEFAULT ''{}'',
        status VARCHAR(50) NOT NULL DEFAULT ''RUNNING'',
        error TEXT,
        started_at TIMESTAMP WITH TIME ZONE NOT NULL,
        completed_at TIMESTAMP WITH TIME ZONE,
        created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
    )';

    -- T1: RICE tables
    EXECUTE 'CREATE TABLE IF NOT EXISTS rice_templates (
        id BIGSERIAL PRIMARY KEY,
        name VARCHAR(100) NOT NULL,
        code VARCHAR(50) NOT NULL UNIQUE,
        strategic_weight DECIMAL(3,2) DEFAULT 1.0,
        active BOOLEAN DEFAULT TRUE,
        created_at TIMESTAMP DEFAULT NOW(),
        updated_at TIMESTAMP DEFAULT NOW()
    )';
    EXECUTE 'CREATE TABLE IF NOT EXISTS rice_criteria (
        id BIGSERIAL PRIMARY KEY,
        template_id BIGINT NOT NULL REFERENCES rice_templates(id),
        parameter VARCHAR(10) NOT NULL,
        name VARCHAR(300) NOT NULL,
        description TEXT,
        selection_type VARCHAR(20) NOT NULL,
        sort_order INTEGER NOT NULL DEFAULT 0,
        active BOOLEAN DEFAULT TRUE,
        created_at TIMESTAMP DEFAULT NOW()
    )';
    EXECUTE 'CREATE TABLE IF NOT EXISTS rice_criteria_options (
        id BIGSERIAL PRIMARY KEY,
        criteria_id BIGINT NOT NULL REFERENCES rice_criteria(id) ON DELETE CASCADE,
        label VARCHAR(500) NOT NULL,
        description TEXT,
        score DECIMAL(10,2) NOT NULL,
        sort_order INTEGER NOT NULL DEFAULT 0
    )';
    EXECUTE 'CREATE TABLE IF NOT EXISTS rice_assessments (
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
    )';
    EXECUTE 'CREATE TABLE IF NOT EXISTS rice_assessment_answers (
        id BIGSERIAL PRIMARY KEY,
        assessment_id BIGINT NOT NULL REFERENCES rice_assessments(id) ON DELETE CASCADE,
        criteria_id BIGINT NOT NULL REFERENCES rice_criteria(id),
        option_id BIGINT NOT NULL REFERENCES rice_criteria_options(id),
        score DECIMAL(10,2) NOT NULL
    )';

    -- Insert RICE seed templates
    EXECUTE 'INSERT INTO rice_templates (id, name, code, strategic_weight) VALUES (1, ''Business'', ''business'', 1.0) ON CONFLICT DO NOTHING';
    EXECUTE 'INSERT INTO rice_templates (id, name, code, strategic_weight) VALUES (2, ''Technical'', ''technical'', 0.8) ON CONFLICT DO NOTHING';
    EXECUTE 'SELECT setval(''rice_templates_id_seq'', GREATEST(2, (SELECT COALESCE(MAX(id),0) FROM rice_templates)))';

    -- T1: member tables
    EXECUTE 'CREATE TABLE IF NOT EXISTS member_competencies (
        id BIGSERIAL PRIMARY KEY,
        team_member_id BIGINT NOT NULL REFERENCES team_members(id) ON DELETE CASCADE,
        component_name VARCHAR(200) NOT NULL,
        level INTEGER NOT NULL DEFAULT 3,
        updated_at TIMESTAMPTZ DEFAULT NOW(),
        UNIQUE(team_member_id, component_name),
        CHECK(level >= 1 AND level <= 5)
    )';
    EXECUTE 'CREATE TABLE IF NOT EXISTS member_absences (
        id BIGSERIAL PRIMARY KEY,
        member_id BIGINT NOT NULL REFERENCES team_members(id) ON DELETE CASCADE,
        absence_type VARCHAR(20) NOT NULL,
        start_date DATE NOT NULL,
        end_date DATE NOT NULL,
        comment VARCHAR(500),
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        CONSTRAINT chk_absence_dates CHECK (end_date >= start_date),
        CONSTRAINT chk_absence_type CHECK (absence_type IN (''VACATION'',''SICK_LEAVE'',''DAY_OFF'',''OTHER''))
    )';

    -- T1: bug_sla_config
    EXECUTE 'CREATE TABLE IF NOT EXISTS bug_sla_config (
        id BIGSERIAL PRIMARY KEY,
        priority VARCHAR(50) NOT NULL UNIQUE,
        max_resolution_hours INTEGER NOT NULL,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    )';
    EXECUTE 'INSERT INTO bug_sla_config (priority, max_resolution_hours) VALUES
        (''Highest'', 24), (''High'', 72), (''Medium'', 168), (''Low'', 336), (''Lowest'', 672)
        ON CONFLICT (priority) DO NOTHING';

    -- T2: tenant_jira_config
    EXECUTE 'CREATE TABLE IF NOT EXISTS tenant_jira_config (
        id BIGSERIAL PRIMARY KEY,
        jira_cloud_id VARCHAR(100),
        jira_base_url VARCHAR(500),
        project_keys TEXT,
        team_field_id VARCHAR(100),
        organization_id VARCHAR(100),
        sync_interval_seconds INT NOT NULL DEFAULT 300,
        connected_by_user_id BIGINT,
        is_active BOOLEAN NOT NULL DEFAULT TRUE,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        jira_email VARCHAR(255),
        jira_api_token VARCHAR(500),
        manual_team_management BOOLEAN NOT NULL DEFAULT FALSE,
        setup_completed BOOLEAN NOT NULL DEFAULT FALSE
    )';

    -- Flyway schema history (so Flyway doesn't try to re-migrate on startup)
    EXECUTE 'CREATE TABLE IF NOT EXISTS flyway_schema_history (
        installed_rank INT NOT NULL,
        version VARCHAR(50),
        description VARCHAR(200) NOT NULL,
        type VARCHAR(20) NOT NULL,
        script VARCHAR(1000) NOT NULL,
        checksum INT,
        installed_by VARCHAR(100) NOT NULL,
        installed_on TIMESTAMP NOT NULL DEFAULT NOW(),
        execution_time INT NOT NULL,
        success BOOLEAN NOT NULL,
        PRIMARY KEY (installed_rank)
    )';
    EXECUTE 'INSERT INTO flyway_schema_history VALUES
        (1, ''1'', ''initial tenant schema'', ''SQL'', ''T1__initial_tenant_schema.sql'', 1053934068, ''perf-seed'', NOW(), 0, TRUE),
        (2, ''2'', ''add tenant jira config'', ''SQL'', ''T2__add_tenant_jira_config.sql'', 1362171610, ''perf-seed'', NOW(), 0, TRUE),
        (3, ''3'', ''add jira credentials'', ''SQL'', ''T3__add_jira_credentials.sql'', -2073729193, ''perf-seed'', NOW(), 0, TRUE),
        (4, ''4'', ''add setup completed'', ''SQL'', ''T4__add_setup_completed.sql'', -90217276, ''perf-seed'', NOW(), 0, TRUE)
    ON CONFLICT (installed_rank) DO NOTHING';

    -- Reset search_path
    EXECUTE 'SET search_path TO public';
END;
$$ LANGUAGE plpgsql;

-- Bootstrap all 3 tenant schemas
SELECT perf_bootstrap_tenant_schema('tenant_perf_alpha');
SELECT perf_bootstrap_tenant_schema('tenant_perf_beta');
SELECT perf_bootstrap_tenant_schema('tenant_perf_gamma');

-- Cleanup helper function
DROP FUNCTION IF EXISTS perf_bootstrap_tenant_schema(TEXT);

SELECT 'Tenant schemas created' AS status;
