-- 05_issues.sql: Generate realistic issues via PL/pgSQL
-- Per tenant: 50 teams × 20 epics × ~15 stories × ~3 subtasks ≈ 61K issues
-- Teams are grouped by project key: teams 1-5 → PERF-A, 6-10 → PERF-B, ... 46-50 → PERF-J
--
-- Realistic data:
-- - Varied stories per epic (8-22), varied subtasks per story (1-5)
-- - Not every story has all 3 roles (20% DEV-only, 30% SA+DEV, 50% SA+DEV+QA)
-- - Estimates from weighted random pools (not fixed)
-- - Correct status consistency: epic→story→subtask
-- - Role-matched assignees (SA subtask → SA member)
-- - Weighted priority distribution (5% Highest, 15% High, 50% Medium, 20% Low, 10% Lowest)
-- - Time logging proportional to progress

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

    -- Status arrays
    epic_statuses TEXT[] := ARRAY['Backlog', 'To Do', 'In Progress', 'Done'];
    story_statuses_pipeline TEXT[] := ARRAY['Backlog', 'Analysis', 'In Development', 'In Testing', 'Done'];

    -- Estimate pools (weighted by repetition)
    sa_estimates INT[] := ARRAY[2, 4, 4, 4, 8, 8, 8, 16];
    dev_estimates INT[] := ARRAY[4, 8, 8, 8, 16, 16, 16, 24, 32];
    qa_estimates INT[] := ARRAY[2, 4, 4, 8, 8, 8, 12];

    -- Rough estimate pools per role (hours)
    rough_sa INT[] := ARRAY[4, 8, 8, 16, 16, 24];
    rough_dev INT[] := ARRAY[8, 16, 16, 24, 32, 40];
    rough_qa INT[] := ARRAY[4, 8, 8, 12, 16];

    e_status_idx INT;
    stories_count INT;
    story_status TEXT;
    sub_status TEXT;
    sub_role TEXT;
    sub_estimate INT;
    sub_logged INT;
    sub_remaining INT;
    story_progress FLOAT;
    priority_roll INT;
    epic_priority TEXT;
    story_priority TEXT;
    pattern_roll INT;

    -- Team member arrays (pre-loaded per team)
    sa_account_ids TEXT[];
    sa_names TEXT[];
    dev_account_ids TEXT[];
    dev_names TEXT[];
    qa_account_ids TEXT[];
    qa_names TEXT[];
    member_idx_pick INT;
    story_assignee_id TEXT;
    story_assignee_name TEXT;
    sub_assignee_id TEXT;
    sub_assignee_name TEXT;

    child_keys TEXT[];
    rough_est JSONB;
    story_est_seconds INT;
    story_role TEXT;
    story_started TIMESTAMPTZ;
    story_done TIMESTAMPTZ;
    base_date TIMESTAMPTZ;
BEGIN
    EXECUTE format('SET search_path TO %I', schema_name);

    FOR team_rec IN SELECT id, name FROM teams ORDER BY id LOOP
        team_count := team_count + 1;
        project_key := project_keys[1 + (team_count - 1) / 5];

        -- Pre-load team members by role
        SELECT array_agg(jira_account_id), array_agg(display_name)
        INTO sa_account_ids, sa_names
        FROM team_members WHERE team_id = team_rec.id AND role = 'SA';

        SELECT array_agg(jira_account_id), array_agg(display_name)
        INTO dev_account_ids, dev_names
        FROM team_members WHERE team_id = team_rec.id AND role = 'DEV';

        SELECT array_agg(jira_account_id), array_agg(display_name)
        INTO qa_account_ids, qa_names
        FROM team_members WHERE team_id = team_rec.id AND role = 'QA';

        -- Fallback if a role is empty
        IF sa_account_ids IS NULL THEN sa_account_ids := dev_account_ids; sa_names := dev_names; END IF;
        IF qa_account_ids IS NULL THEN qa_account_ids := dev_account_ids; qa_names := dev_names; END IF;

        FOR epic_idx IN 1..20 LOOP
            epic_key := project_key || '-' || issue_counter;
            issue_counter := issue_counter + 1;

            -- Epic status: 15% backlog, 10% todo, 55% in_progress, 20% done
            e_status_idx := CASE
                WHEN epic_idx <= 3 THEN 1   -- Backlog
                WHEN epic_idx <= 5 THEN 2   -- To Do
                WHEN epic_idx <= 16 THEN 3  -- In Progress
                ELSE 4                       -- Done
            END;

            -- Weighted priority
            priority_roll := (RANDOM() * 100)::INT;
            epic_priority := CASE
                WHEN priority_roll < 5 THEN 'Highest'
                WHEN priority_roll < 20 THEN 'High'
                WHEN priority_roll < 70 THEN 'Medium'
                WHEN priority_roll < 90 THEN 'Low'
                ELSE 'Lowest'
            END;

            child_keys := ARRAY[]::TEXT[];

            -- Varied stories per epic: 8-22
            stories_count := 8 + (RANDOM() * 14)::INT;

            base_date := NOW() - INTERVAL '90 days' + (epic_idx * 3 || ' days')::INTERVAL;

            INSERT INTO jira_issues (
                issue_key, issue_id, project_key, summary, status, issue_type,
                is_subtask, parent_key, team_id, board_category, workflow_role,
                manual_order,
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
                'EPIC',
                NULL,
                epic_idx,
                epic_priority,
                ROUND((RANDOM() * 80 + 20)::numeric, 2),
                NOW() - INTERVAL '1 day',
                epic_idx = 5,  -- flag one epic per team
                CASE WHEN e_status_idx < 4 THEN CURRENT_DATE + (epic_idx * 7) ELSE NULL END,
                base_date,
                NOW() - INTERVAL '1 day',
                NOW(),
                NOW()
            );

            FOR story_idx IN 1..stories_count LOOP
                story_key := project_key || '-' || issue_counter;
                issue_counter := issue_counter + 1;
                child_keys := array_append(child_keys, story_key);

                -- Determine story status based on epic status
                IF e_status_idx = 4 THEN
                    -- Done epic: all stories Done
                    story_status := 'Done';
                ELSIF e_status_idx <= 2 THEN
                    -- Backlog/To Do epic: all stories Backlog
                    story_status := 'Backlog';
                ELSE
                    -- In Progress epic: stories progress from first (Done) to last (Backlog)
                    story_progress := (story_idx - 1)::FLOAT / stories_count;
                    IF story_progress < 0.15 THEN
                        story_status := 'Done';
                    ELSIF story_progress < 0.30 THEN
                        story_status := 'In Testing';
                    ELSIF story_progress < 0.55 THEN
                        story_status := 'In Development';
                    ELSIF story_progress < 0.75 THEN
                        story_status := 'Analysis';
                    ELSE
                        story_status := 'Backlog';
                    END IF;
                END IF;

                -- Determine story workflow_role from status
                story_role := CASE story_status
                    WHEN 'Analysis' THEN 'SA'
                    WHEN 'In Development' THEN 'DEV'
                    WHEN 'In Testing' THEN 'QA'
                    ELSE NULL
                END;

                -- Story assignee = member of current phase role
                IF story_role = 'SA' THEN
                    member_idx_pick := 1 + (story_idx - 1) % array_length(sa_account_ids, 1);
                    story_assignee_id := sa_account_ids[member_idx_pick];
                    story_assignee_name := sa_names[member_idx_pick];
                ELSIF story_role = 'DEV' THEN
                    member_idx_pick := 1 + (story_idx - 1) % array_length(dev_account_ids, 1);
                    story_assignee_id := dev_account_ids[member_idx_pick];
                    story_assignee_name := dev_names[member_idx_pick];
                ELSIF story_role = 'QA' THEN
                    member_idx_pick := 1 + (story_idx - 1) % array_length(qa_account_ids, 1);
                    story_assignee_id := qa_account_ids[member_idx_pick];
                    story_assignee_name := qa_names[member_idx_pick];
                ELSE
                    -- Backlog/Done: pick a random DEV
                    member_idx_pick := 1 + (story_idx - 1) % array_length(dev_account_ids, 1);
                    story_assignee_id := dev_account_ids[member_idx_pick];
                    story_assignee_name := dev_names[member_idx_pick];
                END IF;

                -- Rough estimates (varied, only for non-backlog)
                IF story_status != 'Backlog' THEN
                    rough_est := jsonb_build_object(
                        'SA', rough_sa[1 + (RANDOM() * (array_length(rough_sa, 1) - 1))::INT],
                        'DEV', rough_dev[1 + (RANDOM() * (array_length(rough_dev, 1) - 1))::INT],
                        'QA', rough_qa[1 + (RANDOM() * (array_length(rough_qa, 1) - 1))::INT]
                    );
                ELSE
                    rough_est := NULL;
                END IF;

                -- Story own estimate (aggregate, not used by planner but exists in real data)
                IF story_status NOT IN ('Backlog') THEN
                    story_est_seconds := (8 + (RANDOM() * 40)::INT) * 3600;
                ELSE
                    story_est_seconds := NULL;
                END IF;

                -- Dates
                IF story_status IN ('Analysis', 'In Development', 'In Testing') THEN
                    story_started := base_date + ((story_idx * 2) || ' days')::INTERVAL;
                    story_done := NULL;
                ELSIF story_status = 'Done' THEN
                    story_started := base_date + ((story_idx * 2) || ' days')::INTERVAL;
                    story_done := story_started + ((5 + (RANDOM() * 10)::INT) || ' days')::INTERVAL;
                ELSE
                    story_started := NULL;
                    story_done := NULL;
                END IF;

                -- Weighted priority for stories
                priority_roll := (RANDOM() * 100)::INT;
                story_priority := CASE
                    WHEN priority_roll < 5 THEN 'Highest'
                    WHEN priority_roll < 20 THEN 'High'
                    WHEN priority_roll < 70 THEN 'Medium'
                    WHEN priority_roll < 90 THEN 'Low'
                    ELSE 'Lowest'
                END;

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
                    story_status,
                    'Story',
                    FALSE,
                    epic_key,
                    team_rec.id,
                    CASE story_status
                        WHEN 'Backlog' THEN 'BACKLOG'
                        WHEN 'Done' THEN 'DONE'
                        ELSE 'IN_PROGRESS'
                    END,
                    story_role,
                    story_priority,
                    story_assignee_id,
                    story_assignee_name,
                    story_est_seconds,
                    CASE
                        WHEN story_status = 'Done' THEN story_est_seconds
                        WHEN story_status IN ('In Testing', 'In Development') THEN (story_est_seconds * (0.3 + RANDOM() * 0.4))::INT
                        WHEN story_status = 'Analysis' THEN (story_est_seconds * RANDOM() * 0.3)::INT
                        ELSE 0
                    END,
                    CASE
                        WHEN story_status = 'Done' THEN 0
                        WHEN story_est_seconds IS NOT NULL THEN GREATEST(0, story_est_seconds - (story_est_seconds * (0.3 + RANDOM() * 0.4))::INT)
                        ELSE 0
                    END,
                    rough_est,
                    FALSE,
                    story_started,
                    story_done,
                    base_date + (story_idx || ' days')::INTERVAL,
                    NOW() - INTERVAL '1 day',
                    NOW(),
                    NOW()
                );

                -- Determine subtask role pattern
                -- 20% DEV-only, 30% SA+DEV, 50% SA+DEV+QA
                pattern_roll := (RANDOM() * 10)::INT;

                -- Generate subtasks based on pattern
                sub_idx := 0;

                -- SA subtasks (if pattern allows)
                IF pattern_roll >= 2 THEN  -- 80% have SA (patterns B and C)
                    sub_idx := sub_idx + 1;
                    sub_key := project_key || '-' || issue_counter;
                    issue_counter := issue_counter + 1;
                    sub_role := 'SA';
                    sub_estimate := sa_estimates[1 + (RANDOM() * (array_length(sa_estimates, 1) - 1))::INT] * 3600;

                    -- Subtask status based on story phase
                    IF story_status IN ('Backlog') THEN
                        sub_status := 'To Do'; sub_logged := 0; sub_remaining := sub_estimate;
                    ELSIF story_status = 'Analysis' THEN
                        -- SA phase: SA subtask in progress
                        sub_status := 'In Progress';
                        sub_logged := (sub_estimate * (0.3 + RANDOM() * 0.4))::INT;
                        sub_remaining := GREATEST(0, sub_estimate - sub_logged);
                    ELSE
                        -- Past SA phase: SA subtask done
                        sub_status := 'Done';
                        sub_logged := (sub_estimate * (0.8 + RANDOM() * 0.4))::INT;
                        sub_remaining := 0;
                    END IF;

                    member_idx_pick := 1 + ((story_idx + sub_idx) - 1) % array_length(sa_account_ids, 1);
                    sub_assignee_id := sa_account_ids[member_idx_pick];
                    sub_assignee_name := sa_names[member_idx_pick];

                    INSERT INTO jira_issues (
                        issue_key, issue_id, project_key, summary, status, issue_type,
                        is_subtask, parent_key, team_id, board_category, workflow_role,
                        priority,
                        assignee_account_id, assignee_display_name,
                        original_estimate_seconds, time_spent_seconds, remaining_estimate_seconds,
                        jira_created_at, jira_updated_at, created_at, updated_at
                    ) VALUES (
                        sub_key, 'id-' || sub_key, project_key,
                        sub_role || ': ' || story_key,
                        sub_status, 'Sub-task', TRUE, story_key, team_rec.id,
                        CASE sub_status WHEN 'To Do' THEN 'PLANNED' WHEN 'In Progress' THEN 'IN_PROGRESS' ELSE 'DONE' END,
                        sub_role, story_priority,
                        sub_assignee_id, sub_assignee_name,
                        sub_estimate, sub_logged, sub_remaining,
                        base_date + (story_idx || ' days')::INTERVAL,
                        NOW() - INTERVAL '1 day', NOW(), NOW()
                    );

                    -- Sometimes add a second SA subtask (20% chance)
                    IF RANDOM() < 0.2 THEN
                        sub_idx := sub_idx + 1;
                        sub_key := project_key || '-' || issue_counter;
                        issue_counter := issue_counter + 1;
                        sub_estimate := sa_estimates[1 + (RANDOM() * (array_length(sa_estimates, 1) - 1))::INT] * 3600;

                        IF story_status IN ('Backlog') THEN
                            sub_status := 'To Do'; sub_logged := 0; sub_remaining := sub_estimate;
                        ELSIF story_status = 'Analysis' THEN
                            IF RANDOM() < 0.5 THEN
                                sub_status := 'Done';
                                sub_logged := (sub_estimate * (0.8 + RANDOM() * 0.4))::INT;
                                sub_remaining := 0;
                            ELSE
                                sub_status := 'To Do'; sub_logged := 0; sub_remaining := sub_estimate;
                            END IF;
                        ELSE
                            sub_status := 'Done';
                            sub_logged := (sub_estimate * (0.8 + RANDOM() * 0.4))::INT;
                            sub_remaining := 0;
                        END IF;

                        member_idx_pick := 1 + ((story_idx + sub_idx) - 1) % array_length(sa_account_ids, 1);

                        INSERT INTO jira_issues (
                            issue_key, issue_id, project_key, summary, status, issue_type,
                            is_subtask, parent_key, team_id, board_category, workflow_role,
                            priority, assignee_account_id, assignee_display_name,
                            original_estimate_seconds, time_spent_seconds, remaining_estimate_seconds,
                            jira_created_at, jira_updated_at, created_at, updated_at
                        ) VALUES (
                            sub_key, 'id-' || sub_key, project_key,
                            sub_role || ' review: ' || story_key,
                            sub_status, 'Sub-task', TRUE, story_key, team_rec.id,
                            CASE sub_status WHEN 'To Do' THEN 'PLANNED' WHEN 'In Progress' THEN 'IN_PROGRESS' ELSE 'DONE' END,
                            sub_role, story_priority,
                            sa_account_ids[member_idx_pick], sa_names[member_idx_pick],
                            sub_estimate, sub_logged, sub_remaining,
                            base_date + (story_idx || ' days')::INTERVAL,
                            NOW() - INTERVAL '1 day', NOW(), NOW()
                        );
                    END IF;
                END IF;

                -- DEV subtasks (always present, 1-2 subtasks)
                FOR dev_sub IN 1..(1 + (RANDOM() < 0.4)::INT) LOOP
                    sub_idx := sub_idx + 1;
                    sub_key := project_key || '-' || issue_counter;
                    issue_counter := issue_counter + 1;
                    sub_role := 'DEV';
                    sub_estimate := dev_estimates[1 + (RANDOM() * (array_length(dev_estimates, 1) - 1))::INT] * 3600;

                    IF story_status IN ('Backlog', 'Analysis') THEN
                        sub_status := 'To Do'; sub_logged := 0; sub_remaining := sub_estimate;
                    ELSIF story_status = 'In Development' THEN
                        IF dev_sub = 1 THEN
                            -- First DEV subtask: in progress
                            sub_status := 'In Progress';
                            sub_logged := (sub_estimate * (0.3 + RANDOM() * 0.5))::INT;
                            sub_remaining := GREATEST(0, sub_estimate - sub_logged);
                        ELSE
                            -- Second DEV subtask: might be to do or in progress
                            IF RANDOM() < 0.5 THEN
                                sub_status := 'To Do'; sub_logged := 0; sub_remaining := sub_estimate;
                            ELSE
                                sub_status := 'In Progress';
                                sub_logged := (sub_estimate * RANDOM() * 0.3)::INT;
                                sub_remaining := GREATEST(0, sub_estimate - sub_logged);
                            END IF;
                        END IF;
                    ELSE
                        -- In Testing or Done: DEV subtasks done
                        sub_status := 'Done';
                        sub_logged := (sub_estimate * (0.8 + RANDOM() * 0.4))::INT;
                        sub_remaining := 0;
                    END IF;

                    member_idx_pick := 1 + ((story_idx + sub_idx) - 1) % array_length(dev_account_ids, 1);
                    sub_assignee_id := dev_account_ids[member_idx_pick];
                    sub_assignee_name := dev_names[member_idx_pick];

                    INSERT INTO jira_issues (
                        issue_key, issue_id, project_key, summary, status, issue_type,
                        is_subtask, parent_key, team_id, board_category, workflow_role,
                        priority, assignee_account_id, assignee_display_name,
                        original_estimate_seconds, time_spent_seconds, remaining_estimate_seconds,
                        jira_created_at, jira_updated_at, created_at, updated_at
                    ) VALUES (
                        sub_key, 'id-' || sub_key, project_key,
                        CASE dev_sub WHEN 1 THEN 'DEV: ' ELSE 'DEV review: ' END || story_key,
                        sub_status, 'Sub-task', TRUE, story_key, team_rec.id,
                        CASE sub_status WHEN 'To Do' THEN 'PLANNED' WHEN 'In Progress' THEN 'IN_PROGRESS' ELSE 'DONE' END,
                        sub_role, story_priority,
                        sub_assignee_id, sub_assignee_name,
                        sub_estimate, sub_logged, sub_remaining,
                        base_date + (story_idx || ' days')::INTERVAL,
                        NOW() - INTERVAL '1 day', NOW(), NOW()
                    );
                END LOOP;

                -- QA subtasks (if pattern allows: 50% of stories)
                IF pattern_roll >= 5 THEN
                    sub_idx := sub_idx + 1;
                    sub_key := project_key || '-' || issue_counter;
                    issue_counter := issue_counter + 1;
                    sub_role := 'QA';
                    sub_estimate := qa_estimates[1 + (RANDOM() * (array_length(qa_estimates, 1) - 1))::INT] * 3600;

                    IF story_status IN ('Backlog', 'Analysis', 'In Development') THEN
                        sub_status := 'To Do'; sub_logged := 0; sub_remaining := sub_estimate;
                    ELSIF story_status = 'In Testing' THEN
                        sub_status := 'In Progress';
                        sub_logged := (sub_estimate * (0.3 + RANDOM() * 0.4))::INT;
                        sub_remaining := GREATEST(0, sub_estimate - sub_logged);
                    ELSE
                        sub_status := 'Done';
                        sub_logged := (sub_estimate * (0.8 + RANDOM() * 0.4))::INT;
                        sub_remaining := 0;
                    END IF;

                    member_idx_pick := 1 + ((story_idx + sub_idx) - 1) % array_length(qa_account_ids, 1);
                    sub_assignee_id := qa_account_ids[member_idx_pick];
                    sub_assignee_name := qa_names[member_idx_pick];

                    INSERT INTO jira_issues (
                        issue_key, issue_id, project_key, summary, status, issue_type,
                        is_subtask, parent_key, team_id, board_category, workflow_role,
                        priority, assignee_account_id, assignee_display_name,
                        original_estimate_seconds, time_spent_seconds, remaining_estimate_seconds,
                        jira_created_at, jira_updated_at, created_at, updated_at
                    ) VALUES (
                        sub_key, 'id-' || sub_key, project_key,
                        'QA: ' || story_key,
                        sub_status, 'Sub-task', TRUE, story_key, team_rec.id,
                        CASE sub_status WHEN 'To Do' THEN 'PLANNED' WHEN 'In Progress' THEN 'IN_PROGRESS' ELSE 'DONE' END,
                        sub_role, story_priority,
                        sub_assignee_id, sub_assignee_name,
                        sub_estimate, sub_logged, sub_remaining,
                        base_date + (story_idx || ' days')::INTERVAL,
                        NOW() - INTERVAL '1 day', NOW(), NOW()
                    );
                END IF;

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

SELECT 'Issues seeded (realistic data, ~60K per tenant)' AS status;
