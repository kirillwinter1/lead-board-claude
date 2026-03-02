-- 07_rice_and_extras.sql: RICE assessments for epics, jira_sync_state

CREATE OR REPLACE FUNCTION perf_seed_extras(schema_name TEXT) RETURNS VOID AS $$
DECLARE
    epic_rec RECORD;
    template_id BIGINT;
BEGIN
    EXECUTE format('SET search_path TO %I', schema_name);

    -- Get business template
    SELECT id INTO template_id FROM rice_templates WHERE code = 'business' LIMIT 1;

    -- RICE assessments for all epics
    FOR epic_rec IN
        SELECT issue_key, auto_score FROM jira_issues WHERE issue_type = 'Epic' ORDER BY issue_key
    LOOP
        INSERT INTO rice_assessments (
            issue_key, template_id, confidence, effort_manual, effort_auto,
            total_reach, total_impact, effective_effort, rice_score, normalized_score
        ) VALUES (
            epic_rec.issue_key,
            template_id,
            0.8,
            CASE (RANDOM() * 3)::INT WHEN 0 THEN 'S' WHEN 1 THEN 'M' WHEN 2 THEN 'L' ELSE 'XL' END,
            ROUND((RANDOM() * 8 + 1)::numeric, 2),
            ROUND((RANDOM() * 10 + 5)::numeric, 2),
            ROUND((RANDOM() * 15 + 5)::numeric, 2),
            ROUND((RANDOM() * 6 + 2)::numeric, 2),
            ROUND((RANDOM() * 50 + 10)::numeric, 2),
            ROUND((RANDOM() * 80 + 20)::numeric, 2)
        )
        ON CONFLICT (issue_key) DO NOTHING;
    END LOOP;

    -- Sync state per project key (10 project keys)
    INSERT INTO jira_sync_state (project_key, last_sync_completed_at, last_sync_issues_count, sync_in_progress)
    VALUES
        ('PERF-A', NOW() - INTERVAL '5 minutes', 61000, FALSE),
        ('PERF-B', NOW() - INTERVAL '5 minutes', 61000, FALSE),
        ('PERF-C', NOW() - INTERVAL '5 minutes', 61000, FALSE),
        ('PERF-D', NOW() - INTERVAL '5 minutes', 61000, FALSE),
        ('PERF-E', NOW() - INTERVAL '5 minutes', 61000, FALSE),
        ('PERF-F', NOW() - INTERVAL '5 minutes', 61000, FALSE),
        ('PERF-G', NOW() - INTERVAL '5 minutes', 61000, FALSE),
        ('PERF-H', NOW() - INTERVAL '5 minutes', 61000, FALSE),
        ('PERF-I', NOW() - INTERVAL '5 minutes', 61000, FALSE),
        ('PERF-J', NOW() - INTERVAL '5 minutes', 61000, FALSE)
    ON CONFLICT (project_key) DO NOTHING;

    EXECUTE 'SET search_path TO public';
END;
$$ LANGUAGE plpgsql;

SELECT perf_seed_extras('tenant_perf_alpha');
SELECT perf_seed_extras('tenant_perf_beta');
SELECT perf_seed_extras('tenant_perf_gamma');

DROP FUNCTION IF EXISTS perf_seed_extras(TEXT);

SELECT 'RICE and extras seeded (10 project keys)' AS status;
