-- F82: tenant membership lifecycle tied to Jira access (SECURITY_AUDIT.md §2).
-- A tenant_users row is no longer permanent once created: it can be deactivated
-- (not deleted) when the user's Atlassian account loses access to the tenant's
-- Jira site, and reactivated if access is restored. See
-- ai-ru/features/F82_JIRA_ACCESS_MEMBERSHIP.md.

ALTER TABLE tenant_users
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN deactivated_at TIMESTAMPTZ NULL,
    ADD COLUMN deactivated_reason VARCHAR(255) NULL;

CREATE INDEX idx_tenant_users_active ON tenant_users(active);
