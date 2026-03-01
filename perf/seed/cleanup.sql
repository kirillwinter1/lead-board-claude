-- cleanup.sql: Remove all perf test data
-- Safe: only touches tenant_perf_* schemas and perf-related rows in public

-- Drop perf tenant schemas
DROP SCHEMA IF EXISTS tenant_perf_alpha CASCADE;
DROP SCHEMA IF EXISTS tenant_perf_beta CASCADE;
DROP SCHEMA IF EXISTS tenant_perf_gamma CASCADE;

-- Remove perf sessions
DELETE FROM public.user_sessions WHERE id LIKE 'perf-session-%';

-- Remove perf tenant_users (cascade from tenants will handle this, but explicit for safety)
DELETE FROM public.tenant_users WHERE tenant_id IN (
    SELECT id FROM public.tenants WHERE slug IN ('perf-alpha', 'perf-beta', 'perf-gamma')
);

-- Remove perf tenants
DELETE FROM public.tenants WHERE slug IN ('perf-alpha', 'perf-beta', 'perf-gamma');

-- Remove perf users
DELETE FROM public.users WHERE atlassian_account_id LIKE 'perf-%';

SELECT 'Cleanup complete' AS status;
