-- T3: Add Jira credentials to tenant config (moved from .env to DB)

ALTER TABLE tenant_jira_config ADD COLUMN jira_email VARCHAR(255);
ALTER TABLE tenant_jira_config ADD COLUMN jira_api_token VARCHAR(500);
ALTER TABLE tenant_jira_config ADD COLUMN manual_team_management BOOLEAN NOT NULL DEFAULT FALSE;

-- Make project_keys nullable (not yet configured for new tenants)
ALTER TABLE tenant_jira_config ALTER COLUMN project_keys DROP NOT NULL;
