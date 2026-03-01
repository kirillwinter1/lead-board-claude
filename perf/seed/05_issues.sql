-- 05_issues.sql: Generate issues via PL/pgSQL
-- Per tenant: 50 teams × 20 epics × 15 stories × 3 subtasks = 61,000 issues
-- Teams are grouped by project key: teams 1-5 → PERF-A, 6-10 → PERF-B, ... 46-50 → PERF-J

CREATE OR REPLACE FUNCTION perf_seed_issues(schema_name TEXT) RETURNS VOID AS $$
DECLARE
    team_rec RECORD;
    team_count INT := 0;
    epic_idx INT;
    story_idx INT;
    sub_idx INT;
    project_keys TEXT[] := ARRAY['PERF-A','PERF-B','PERF-C','PERF-D','PERF-E','PERF-F','PERF-G','PERF-H','PERF-I','PERF-J'];
    project_key TEXT;
    epic_key TEXT;
    story_key TEXT;
    sub_key TEXT;
    issue_counter INT := 1;
    epic_statuses TEXT[] := ARRAY['Backlog', 'To Do', 'In Progress', 'Done'];
    story_statuses TEXT[] := ARRAY['Backlog', 'Analysis', 'In Development', 'In Testing', 'Done'];
    subtask_statuses TEXT[] := ARRAY['To Do', 'In Progress', 'Done'];
    epic_categories TEXT[] := ARRAY['BACKLOG', 'PLANNED', 'IN_PROGRESS', 'DONE'];
    story_categories TEXT[] := ARRAY['BACKLOG', 'IN_PROGRESS', 'IN_PROGRESS', 'IN_PROGRESS', 'DONE'];
    sub_categories TEXT[] := ARRAY['PLANNED', 'IN_PROGRESS', 'DONE'];
    story_roles TEXT[] := ARRAY[NULL, 'SA', 'DEV', 'QA', NULL];
    priorities TEXT[] := ARRAY['Highest', 'High', 'Medium', 'Low', 'Lowest'];
    e_status_idx INT;
    s_status_idx INT;
    st_status_idx INT;
    member_rec RECORD;
    child_keys TEXT[];
BEGIN
    EXECUTE format('SET search_path TO %I', schema_name);

    FOR team_rec IN SELECT id, name FROM teams ORDER BY id LOOP
        team_count := team_count + 1;
        -- Each 5 teams share a project key: teams 1-5 → PERF-A, 6-10 → PERF-B, etc.
        project_key := project_keys[1 + (team_count - 1) / 5];

        FOR epic_idx IN 1..20 LOOP
            epic_key := project_key || '-' || issue_counter;
            issue_counter := issue_counter + 1;

            -- Epic status distribution: 20% backlog, 20% todo, 40% in_progress, 20% done
            e_status_idx := CASE
                WHEN epic_idx <= 4 THEN 1
                WHEN epic_idx <= 8 THEN 2
                WHEN epic_idx <= 16 THEN 3
                ELSE 4
            END;

            -- Collect story keys for child_epic_keys
            child_keys := ARRAY[]::TEXT[];

            INSERT INTO jira_issues (
                issue_key, issue_id, project_key, summary, status, issue_type,
                is_subtask, parent_key, team_id, board_category, workflow_role,
                priority, auto_score, auto_score_calculated_at,
                flagged, due_date,
                jira_created_at, jira_updated_at, created_at, updated_at
            ) VALUES (
                epic_key,
                'id-' || epic_key,
                project_key,
                'Epic ' || epic_idx || ' — ' || team_rec.name,
                epic_statuses[e_status_idx],
                'Epic',
                FALSE,
                NULL,
                team_rec.id,
                epic_categories[e_status_idx],
                NULL,
                priorities[1 + (epic_idx - 1) % 5],
                ROUND((RANDOM() * 80 + 20)::numeric, 2),
                NOW() - INTERVAL '1 day',
                epic_idx = 5, -- flag one epic per team
                CASE WHEN e_status_idx < 4 THEN CURRENT_DATE + (epic_idx * 7) ELSE NULL END,
                NOW() - INTERVAL '60 days' + (epic_idx || ' days')::INTERVAL,
                NOW() - INTERVAL '1 day',
                NOW(),
                NOW()
            );

            FOR story_idx IN 1..15 LOOP
                story_key := project_key || '-' || issue_counter;
                issue_counter := issue_counter + 1;
                child_keys := array_append(child_keys, story_key);

                -- Story status depends on epic status
                IF e_status_idx = 4 THEN
                    s_status_idx := 5; -- Done epic = all stories done
                ELSIF e_status_idx = 1 THEN
                    s_status_idx := 1; -- Backlog epic = all stories backlog
                ELSE
                    -- Distribute: 20% backlog, 20% analysis, 30% dev, 20% testing, 10% done
                    s_status_idx := CASE
                        WHEN story_idx <= 3 THEN 1
                        WHEN story_idx <= 6 THEN 2
                        WHEN story_idx <= 10 THEN 3
                        WHEN story_idx <= 13 THEN 4
                        ELSE 5
                    END;
                END IF;

                -- Pick a member from the team for assignment
                SELECT jira_account_id, display_name INTO member_rec
                FROM team_members WHERE team_id = team_rec.id
                ORDER BY id OFFSET (story_idx - 1) % 10 LIMIT 1;

                INSERT INTO jira_issues (
                    issue_key, issue_id, project_key, summary, status, issue_type,
                    is_subtask, parent_key, team_id, board_category, workflow_role,
                    priority,
                    assignee_account_id, assignee_display_name,
                    original_estimate_seconds, time_spent_seconds, remaining_estimate_seconds,
                    rough_estimates,
                    flagged,
                    started_at, done_at,
                    jira_created_at, jira_updated_at, created_at, updated_at
                ) VALUES (
                    story_key,
                    'id-' || story_key,
                    project_key,
                    'Story ' || story_idx || ' of Epic ' || epic_idx || ' — ' || team_rec.name,
                    story_statuses[s_status_idx],
                    'Story',
                    FALSE,
                    epic_key,
                    team_rec.id,
                    story_categories[s_status_idx],
                    story_roles[s_status_idx],
                    priorities[1 + (story_idx - 1) % 5],
                    member_rec.jira_account_id,
                    member_rec.display_name,
                    CASE WHEN s_status_idx >= 2 THEN (8 + story_idx) * 3600 ELSE NULL END,
                    CASE WHEN s_status_idx >= 3 THEN story_idx * 3600 ELSE 0 END,
                    CASE WHEN s_status_idx >= 2 AND s_status_idx < 5 THEN (8 - story_idx % 5) * 3600 ELSE 0 END,
                    CASE WHEN s_status_idx >= 2 THEN
                        jsonb_build_object('SA', 8, 'DEV', 16, 'QA', 8)
                    ELSE NULL END,
                    FALSE,
                    CASE WHEN s_status_idx >= 2 THEN NOW() - ((30 - story_idx) || ' days')::INTERVAL ELSE NULL END,
                    CASE WHEN s_status_idx = 5 THEN NOW() - ((15 - story_idx) || ' days')::INTERVAL ELSE NULL END,
                    NOW() - INTERVAL '50 days' + (story_idx || ' days')::INTERVAL,
                    NOW() - INTERVAL '1 day',
                    NOW(),
                    NOW()
                );

                -- Subtasks
                FOR sub_idx IN 1..3 LOOP
                    sub_key := project_key || '-' || issue_counter;
                    issue_counter := issue_counter + 1;

                    -- Subtask status tracks story progress
                    IF s_status_idx = 5 THEN
                        st_status_idx := 3; -- all done
                    ELSIF s_status_idx = 1 THEN
                        st_status_idx := 1; -- all todo
                    ELSE
                        st_status_idx := CASE
                            WHEN sub_idx = 1 THEN 3  -- first subtask done
                            WHEN sub_idx = 2 THEN 2  -- second in progress
                            ELSE 1                     -- third todo
                        END;
                    END IF;

                    INSERT INTO jira_issues (
                        issue_key, issue_id, project_key, summary, status, issue_type,
                        is_subtask, parent_key, team_id, board_category, workflow_role,
                        priority,
                        assignee_account_id, assignee_display_name,
                        original_estimate_seconds, time_spent_seconds, remaining_estimate_seconds,
                        jira_created_at, jira_updated_at, created_at, updated_at
                    ) VALUES (
                        sub_key,
                        'id-' || sub_key,
                        project_key,
                        'Subtask ' || sub_idx || ' of ' || story_key,
                        subtask_statuses[st_status_idx],
                        'Sub-task',
                        TRUE,
                        story_key,
                        team_rec.id,
                        sub_categories[st_status_idx],
                        CASE sub_idx WHEN 1 THEN 'SA' WHEN 2 THEN 'DEV' ELSE 'QA' END,
                        priorities[1 + (sub_idx - 1) % 5],
                        member_rec.jira_account_id,
                        member_rec.display_name,
                        sub_idx * 4 * 3600,
                        CASE WHEN st_status_idx >= 2 THEN sub_idx * 2 * 3600 ELSE 0 END,
                        CASE WHEN st_status_idx = 1 THEN sub_idx * 4 * 3600
                             WHEN st_status_idx = 2 THEN sub_idx * 2 * 3600
                             ELSE 0 END,
                        NOW() - INTERVAL '45 days' + (sub_idx || ' days')::INTERVAL,
                        NOW() - INTERVAL '1 day',
                        NOW(),
                        NOW()
                    );
                END LOOP; -- subtasks
            END LOOP; -- stories

            -- Update epic with child_epic_keys
            UPDATE jira_issues SET child_epic_keys = child_keys
            WHERE issue_key = epic_key;

        END LOOP; -- epics
    END LOOP; -- teams

    EXECUTE 'SET search_path TO public';
END;
$$ LANGUAGE plpgsql;

SELECT perf_seed_issues('tenant_perf_alpha');
SELECT perf_seed_issues('tenant_perf_beta');
SELECT perf_seed_issues('tenant_perf_gamma');

DROP FUNCTION IF EXISTS perf_seed_issues(TEXT);

SELECT 'Issues seeded (~61,000 per tenant)' AS status;
