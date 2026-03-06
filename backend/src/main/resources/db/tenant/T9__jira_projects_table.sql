CREATE TABLE IF NOT EXISTS jira_projects (
    id BIGSERIAL PRIMARY KEY,
    project_key VARCHAR(50) NOT NULL UNIQUE,
    display_name VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sync_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed from jira_sync_state (always present in tenant schemas)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = 'jira_sync_state') THEN
        INSERT INTO jira_projects (project_key, display_name)
        SELECT DISTINCT project_key, project_key FROM jira_sync_state
        WHERE project_key IS NOT NULL
        ON CONFLICT DO NOTHING;
    END IF;
END $$;
