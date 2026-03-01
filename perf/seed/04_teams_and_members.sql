-- 04_teams_and_members.sql: 50 teams × 10 members per tenant
-- Structure: 10 project keys × 5 teams each = 50 teams
-- Each team has 10 members (2 SA, 5 DEV, 3 QA)

CREATE OR REPLACE FUNCTION perf_seed_teams(schema_name TEXT, tenant_prefix TEXT) RETURNS VOID AS $$
DECLARE
    project_letters TEXT[] := ARRAY['A','B','C','D','E','F','G','H','I','J'];
    team_colors TEXT[] := ARRAY[
        '#3b82f6', '#22c55e', '#f59e0b', '#ef4444', '#8b5cf6',
        '#06b6d4', '#ec4899', '#84cc16', '#f97316', '#14b8a6',
        '#6366f1', '#a855f7'
    ];
    roles TEXT[] := ARRAY['SA', 'SA', 'DEV', 'DEV', 'DEV', 'DEV', 'DEV', 'QA', 'QA', 'QA'];
    grades TEXT[] := ARRAY['SENIOR', 'MIDDLE', 'SENIOR', 'SENIOR', 'MIDDLE', 'MIDDLE', 'JUNIOR', 'SENIOR', 'MIDDLE', 'MIDDLE'];
    hours DECIMAL[] := ARRAY[7.5, 6.0, 8.0, 7.0, 6.0, 5.0, 8.0, 7.5, 6.0, 7.0];
    pk_idx INT;
    t INT;
    m INT;
    team_id BIGINT;
    member_idx INT;
    global_team INT;
BEGIN
    EXECUTE format('SET search_path TO %I', schema_name);

    global_team := 0;
    FOR pk_idx IN 1..10 LOOP
        FOR t IN 1..5 LOOP
            global_team := global_team + 1;

            INSERT INTO teams (name, jira_team_value, active, color, planning_config)
            VALUES (
                project_letters[pk_idx] || '-Team ' || t,
                'perf-team-' || global_team,
                TRUE,
                team_colors[1 + (global_team - 1) % 12],
                '{"gradeCoefficients": {"senior": 0.8, "middle": 1.0, "junior": 1.5}, "riskBuffer": 0.2, "wipLimits": {"team": 6, "roleLimits": {"SA": 2, "DEV": 3, "QA": 2}}, "storyDuration": {"roleDurations": {"SA": 2, "DEV": 3, "QA": 2}}}'::jsonb
            )
            RETURNING id INTO team_id;

            FOR m IN 1..10 LOOP
                member_idx := (global_team - 1) * 10 + m;
                INSERT INTO team_members (team_id, jira_account_id, display_name, role, grade, hours_per_day, active, avatar_url)
                VALUES (
                    team_id,
                    'perf-' || tenant_prefix || '-member-' || LPAD(member_idx::TEXT, 4, '0'),
                    project_letters[pk_idx] || '-Team' || t || ' Member ' || m,
                    roles[m],
                    grades[m],
                    hours[m],
                    TRUE,
                    'https://avatar.example.com/' || tenant_prefix || '/' || member_idx || '.png'
                );
            END LOOP;
        END LOOP;
    END LOOP;

    EXECUTE 'SET search_path TO public';
END;
$$ LANGUAGE plpgsql;

SELECT perf_seed_teams('tenant_perf_alpha', 't1');
SELECT perf_seed_teams('tenant_perf_beta', 't2');
SELECT perf_seed_teams('tenant_perf_gamma', 't3');

DROP FUNCTION IF EXISTS perf_seed_teams(TEXT, TEXT);

SELECT 'Teams seeded (50 teams × 10 members per tenant)' AS status;
