-- 03_workflow_config.sql: Workflow roles, issue type mappings, status mappings
-- Per-project configs (F48): 10 project_configurations per tenant (PERF-A through PERF-J)

CREATE OR REPLACE FUNCTION perf_seed_workflow_config(schema_name TEXT) RETURNS VOID AS $$
DECLARE
    default_config_id BIGINT;
    cur_config_id BIGINT;
    project_keys TEXT[] := ARRAY['PERF-A','PERF-B','PERF-C','PERF-D','PERF-E','PERF-F','PERF-G','PERF-H','PERF-I','PERF-J'];
    pk TEXT;
BEGIN
    EXECUTE format('SET search_path TO %I', schema_name);

    -- Get default config
    SELECT id INTO default_config_id FROM project_configurations WHERE is_default = TRUE LIMIT 1;

    -- ============================================================
    -- Default config: workflow roles
    -- ============================================================
    INSERT INTO workflow_roles (config_id, code, display_name, color, sort_order, is_default) VALUES
        (default_config_id, 'SA', 'System Analysis', '#3b82f6', 1, FALSE),
        (default_config_id, 'DEV', 'Development', '#22c55e', 2, FALSE),
        (default_config_id, 'QA', 'Quality Assurance', '#f59e0b', 3, FALSE);

    -- ============================================================
    -- Default config: issue type mappings
    -- ============================================================
    INSERT INTO issue_type_mappings (config_id, jira_type_name, board_category, workflow_role_code) VALUES
        (default_config_id, 'Epic', 'EPIC', NULL),
        (default_config_id, 'Story', 'STORY', NULL),
        (default_config_id, 'Task', 'STORY', NULL),
        (default_config_id, 'Sub-task', 'SUBTASK', NULL),
        (default_config_id, 'Bug', 'BUG', NULL);

    -- ============================================================
    -- Default config: status mappings (EPIC + STORY + SUBTASK)
    -- ============================================================
    -- Epic statuses
    INSERT INTO status_mappings (config_id, jira_status_name, issue_category, status_category, workflow_role_code, sort_order, score_weight, color) VALUES
        (default_config_id, 'Backlog', 'EPIC', 'BACKLOG', NULL, 0, 0, '#94a3b8'),
        (default_config_id, 'To Do', 'EPIC', 'PLANNED', NULL, 1, 10, '#60a5fa'),
        (default_config_id, 'In Progress', 'EPIC', 'IN_PROGRESS', NULL, 2, 50, '#fbbf24'),
        (default_config_id, 'Done', 'EPIC', 'DONE', NULL, 3, 100, '#4ade80');
    -- Story statuses
    INSERT INTO status_mappings (config_id, jira_status_name, issue_category, status_category, workflow_role_code, sort_order, score_weight, color) VALUES
        (default_config_id, 'Backlog', 'STORY', 'BACKLOG', NULL, 0, 0, '#94a3b8'),
        (default_config_id, 'Analysis', 'STORY', 'IN_PROGRESS', 'SA', 1, 20, '#3b82f6'),
        (default_config_id, 'In Development', 'STORY', 'IN_PROGRESS', 'DEV', 2, 50, '#22c55e'),
        (default_config_id, 'In Testing', 'STORY', 'IN_PROGRESS', 'QA', 3, 80, '#f59e0b'),
        (default_config_id, 'Done', 'STORY', 'DONE', NULL, 4, 100, '#4ade80');
    -- Subtask statuses
    INSERT INTO status_mappings (config_id, jira_status_name, issue_category, status_category, workflow_role_code, sort_order, score_weight, color) VALUES
        (default_config_id, 'To Do', 'SUBTASK', 'PLANNED', NULL, 0, 0, '#94a3b8'),
        (default_config_id, 'In Progress', 'SUBTASK', 'IN_PROGRESS', NULL, 1, 50, '#fbbf24'),
        (default_config_id, 'Done', 'SUBTASK', 'DONE', NULL, 2, 100, '#4ade80');

    -- ============================================================
    -- Per-project configurations (10 projects: PERF-A through PERF-J)
    -- ============================================================
    FOREACH pk IN ARRAY project_keys LOOP
        INSERT INTO project_configurations (name, is_default, project_key)
        VALUES (pk || ' Config', FALSE, pk) RETURNING id INTO cur_config_id;

        -- Workflow roles
        INSERT INTO workflow_roles (config_id, code, display_name, color, sort_order, is_default) VALUES
            (cur_config_id, 'SA', 'Analysis', '#3b82f6', 1, FALSE),
            (cur_config_id, 'DEV', 'Development', '#22c55e', 2, FALSE),
            (cur_config_id, 'QA', 'Testing', '#f59e0b', 3, FALSE);

        -- Issue type mappings
        INSERT INTO issue_type_mappings (config_id, jira_type_name, board_category, workflow_role_code) VALUES
            (cur_config_id, 'Epic', 'EPIC', NULL),
            (cur_config_id, 'Story', 'STORY', NULL),
            (cur_config_id, 'Sub-task', 'SUBTASK', NULL),
            (cur_config_id, 'Bug', 'BUG', NULL);

        -- Status mappings (copy from default config)
        INSERT INTO status_mappings (config_id, jira_status_name, issue_category, status_category, workflow_role_code, sort_order, score_weight, color)
        SELECT cur_config_id, s.jira_status_name, s.issue_category, s.status_category, s.workflow_role_code, s.sort_order, s.score_weight, s.color
        FROM status_mappings s WHERE s.config_id = default_config_id;
    END LOOP;

    -- ============================================================
    -- Link type mappings
    -- ============================================================
    INSERT INTO link_type_mappings (config_id, jira_link_type_name, link_category) VALUES
        (default_config_id, 'Blocks', 'BLOCKS'),
        (default_config_id, 'is blocked by', 'BLOCKS'),
        (default_config_id, 'Relates', 'RELATED');

    -- Jira config (F48 multi-project with 10 keys)
    INSERT INTO tenant_jira_config (jira_base_url, project_keys, manual_team_management, setup_completed, is_active)
    VALUES ('https://perf-test.atlassian.net', 'PERF-A,PERF-B,PERF-C,PERF-D,PERF-E,PERF-F,PERF-G,PERF-H,PERF-I,PERF-J', TRUE, TRUE, TRUE);

    EXECUTE 'SET search_path TO public';
END;
$$ LANGUAGE plpgsql;

SELECT perf_seed_workflow_config('tenant_perf_alpha');
SELECT perf_seed_workflow_config('tenant_perf_beta');
SELECT perf_seed_workflow_config('tenant_perf_gamma');

DROP FUNCTION IF EXISTS perf_seed_workflow_config(TEXT);

SELECT 'Workflow config seeded (10 project keys per tenant)' AS status;
