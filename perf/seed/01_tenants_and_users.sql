-- 01_tenants_and_users.sql: Create perf test users, tenants, tenant_users, sessions
-- All in public schema

-- ============================================================
-- 1. Users (300 total: 100 per tenant)
-- ============================================================
DO $$
DECLARE
    tenant_prefix TEXT;
    prefixes TEXT[] := ARRAY['t1', 't2', 't3'];
    i INT;
    user_id BIGINT;
BEGIN
    FOREACH tenant_prefix IN ARRAY prefixes LOOP
        FOR i IN 1..100 LOOP
            INSERT INTO public.users (atlassian_account_id, email, display_name, avatar_url, app_role)
            VALUES (
                'perf-' || tenant_prefix || '-u' || LPAD(i::TEXT, 3, '0'),
                'perf_' || tenant_prefix || '_u' || LPAD(i::TEXT, 3, '0') || '@test.local',
                'Perf User ' || tenant_prefix || '-' || i,
                'https://avatar.example.com/perf/' || tenant_prefix || '/' || i || '.png',
                CASE WHEN i = 1 THEN 'ADMIN' WHEN i <= 3 THEN 'TEAM_LEAD' ELSE 'MEMBER' END
            )
            ON CONFLICT (atlassian_account_id) DO NOTHING;
        END LOOP;
    END LOOP;
END $$;

-- ============================================================
-- 2. Tenants
-- ============================================================
INSERT INTO public.tenants (slug, name, schema_name, plan, is_active)
VALUES
    ('perf-alpha', 'Perf Alpha Corp', 'tenant_perf_alpha', 'ENTERPRISE', TRUE),
    ('perf-beta', 'Perf Beta Inc', 'tenant_perf_beta', 'ENTERPRISE', TRUE),
    ('perf-gamma', 'Perf Gamma Ltd', 'tenant_perf_gamma', 'ENTERPRISE', TRUE)
ON CONFLICT (slug) DO NOTHING;

-- ============================================================
-- 3. Tenant users (link users to tenants)
-- ============================================================
DO $$
DECLARE
    t_slug TEXT;
    t_prefix TEXT;
    slugs TEXT[] := ARRAY['perf-alpha', 'perf-beta', 'perf-gamma'];
    prefixes TEXT[] := ARRAY['t1', 't2', 't3'];
    t_id BIGINT;
    u_id BIGINT;
    i INT;
    idx INT := 1;
BEGIN
    FOREACH t_slug IN ARRAY slugs LOOP
        t_prefix := prefixes[idx];
        SELECT id INTO t_id FROM public.tenants WHERE slug = t_slug;

        FOR i IN 1..100 LOOP
            SELECT id INTO u_id FROM public.users
            WHERE atlassian_account_id = 'perf-' || t_prefix || '-u' || LPAD(i::TEXT, 3, '0');

            INSERT INTO public.tenant_users (tenant_id, user_id, app_role)
            VALUES (
                t_id, u_id,
                CASE WHEN i = 1 THEN 'ADMIN' WHEN i <= 3 THEN 'TEAM_LEAD' ELSE 'MEMBER' END
            )
            ON CONFLICT (tenant_id, user_id) DO NOTHING;
        END LOOP;

        idx := idx + 1;
    END LOOP;
END $$;

-- ============================================================
-- 4. Sessions (deterministic IDs for k6)
-- ============================================================
DO $$
DECLARE
    t_slug TEXT;
    t_prefix TEXT;
    slug_name TEXT;
    slugs TEXT[] := ARRAY['perf-alpha', 'perf-beta', 'perf-gamma'];
    prefixes TEXT[] := ARRAY['t1', 't2', 't3'];
    slug_names TEXT[] := ARRAY['alpha', 'beta', 'gamma'];
    t_id BIGINT;
    u_id BIGINT;
    i INT;
    idx INT := 1;
    session_id TEXT;
BEGIN
    FOREACH t_slug IN ARRAY slugs LOOP
        t_prefix := prefixes[idx];
        slug_name := slug_names[idx];
        SELECT id INTO t_id FROM public.tenants WHERE slug = t_slug;

        FOR i IN 1..100 LOOP
            SELECT id INTO u_id FROM public.users
            WHERE atlassian_account_id = 'perf-' || t_prefix || '-u' || LPAD(i::TEXT, 3, '0');

            session_id := 'perf-session-' || slug_name || '-u' || LPAD(i::TEXT, 3, '0');

            INSERT INTO public.user_sessions (id, user_id, tenant_id, created_at, expires_at)
            VALUES (
                session_id,
                u_id,
                t_id,
                NOW(),
                NOW() + INTERVAL '30 days'
            )
            ON CONFLICT (id) DO UPDATE SET expires_at = NOW() + INTERVAL '30 days';
        END LOOP;

        idx := idx + 1;
    END LOOP;
END $$;

SELECT 'Tenants and users created' AS status;
