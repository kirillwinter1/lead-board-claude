-- T2: Jira connection config per tenant

CREATE TABLE tenant_jira_config (
    id BIGSERIAL PRIMARY KEY,
    jira_cloud_id VARCHAR(100),
    jira_base_url VARCHAR(500),
    project_keys TEXT NOT NULL,
    team_field_id VARCHAR(100),
    organization_id VARCHAR(100),
    sync_interval_seconds INT NOT NULL DEFAULT 300,
    connected_by_user_id BIGINT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
