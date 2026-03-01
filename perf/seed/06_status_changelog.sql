-- 06_status_changelog.sql: Generate status transitions for metrics/timeline
-- Creates ~5000 changelog entries per tenant based on issue statuses

CREATE OR REPLACE FUNCTION perf_seed_changelog(schema_name TEXT) RETURNS VOID AS $$
DECLARE
    issue_rec RECORD;
    story_statuses TEXT[] := ARRAY['Backlog', 'Analysis', 'In Development', 'In Testing', 'Done'];
    sub_statuses TEXT[] := ARRAY['To Do', 'In Progress', 'Done'];
    prev_status TEXT;
    curr_idx INT;
    transition_time TIMESTAMPTZ;
    time_in_prev BIGINT;
BEGIN
    EXECUTE format('SET search_path TO %I', schema_name);

    -- Story transitions
    FOR issue_rec IN
        SELECT issue_key, issue_id, status, jira_created_at, started_at, done_at
        FROM jira_issues
        WHERE issue_type = 'Story' AND status != 'Backlog'
        ORDER BY issue_key
    LOOP
        -- Determine how many statuses to generate transitions for
        curr_idx := CASE issue_rec.status
            WHEN 'Analysis' THEN 2
            WHEN 'In Development' THEN 3
            WHEN 'In Testing' THEN 4
            WHEN 'Done' THEN 5
            ELSE 1
        END;

        transition_time := COALESCE(issue_rec.jira_created_at, NOW() - INTERVAL '30 days');
        prev_status := NULL;

        FOR i IN 1..curr_idx LOOP
            time_in_prev := CASE WHEN prev_status IS NULL THEN NULL
                ELSE EXTRACT(EPOCH FROM (transition_time - (transition_time - (i * 2 || ' days')::INTERVAL)))::BIGINT
            END;

            -- Space transitions 2-5 days apart
            transition_time := transition_time + ((2 + (i % 4)) || ' days')::INTERVAL;

            INSERT INTO status_changelog (issue_key, issue_id, from_status, to_status, transitioned_at, time_in_previous_status_seconds, source)
            VALUES (
                issue_rec.issue_key,
                issue_rec.issue_id,
                prev_status,
                story_statuses[i],
                transition_time,
                CASE WHEN prev_status IS NULL THEN NULL ELSE (2 + (i % 4)) * 86400 END,
                'SYNC'
            )
            ON CONFLICT DO NOTHING;

            prev_status := story_statuses[i];
        END LOOP;
    END LOOP;

    -- Subtask transitions
    FOR issue_rec IN
        SELECT issue_key, issue_id, status, jira_created_at
        FROM jira_issues
        WHERE issue_type = 'Sub-task' AND status != 'To Do'
        ORDER BY issue_key
    LOOP
        curr_idx := CASE issue_rec.status
            WHEN 'In Progress' THEN 2
            WHEN 'Done' THEN 3
            ELSE 1
        END;

        transition_time := COALESCE(issue_rec.jira_created_at, NOW() - INTERVAL '20 days');
        prev_status := NULL;

        FOR i IN 1..curr_idx LOOP
            transition_time := transition_time + ((1 + (i % 3)) || ' days')::INTERVAL;

            INSERT INTO status_changelog (issue_key, issue_id, from_status, to_status, transitioned_at, time_in_previous_status_seconds, source)
            VALUES (
                issue_rec.issue_key,
                issue_rec.issue_id,
                prev_status,
                sub_statuses[i],
                transition_time,
                CASE WHEN prev_status IS NULL THEN NULL ELSE (1 + (i % 3)) * 86400 END,
                'SYNC'
            )
            ON CONFLICT DO NOTHING;

            prev_status := sub_statuses[i];
        END LOOP;
    END LOOP;

    EXECUTE 'SET search_path TO public';
END;
$$ LANGUAGE plpgsql;

SELECT perf_seed_changelog('tenant_perf_alpha');
SELECT perf_seed_changelog('tenant_perf_beta');
SELECT perf_seed_changelog('tenant_perf_gamma');

DROP FUNCTION IF EXISTS perf_seed_changelog(TEXT);

SELECT 'Status changelog seeded' AS status;
