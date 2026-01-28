-- Test data for Lead Board
-- Covers last 3 months with realistic time logging and completions

-- Team 4 members:
-- SA: 557058:0bf7607a-80c5-4aaf-9bb0-22f12379338b (test SA)
-- DEV: 60465e032b3f9a006a25baa7 (Анна Кардакова)
-- QA: 712020:4ef2cfc1-9fcd-43ee-a4d9-d2dc43a39825 (Vladislav)

-- Team 3 members:
-- DEV: 70121:b40ff773-75a6-4521-b351-6b0114b87dd4 (Kirill Reshetov)
-- QA: 712020:c7f55b2b-0935-4fa4-9a94-7e9cc4f91c76 (Елисей)
-- SA: 712020:f7d73d50-0577-4580-94bc-5a8a6a10ddf8 (Александр)

-- Clean up old DEMO/TEST data
DELETE FROM forecast_snapshots WHERE team_id IN (3, 4);
DELETE FROM status_changelog WHERE issue_key LIKE 'DEMO%' OR issue_key LIKE 'TEST%';
DELETE FROM jira_issues WHERE issue_key LIKE 'DEMO%' OR issue_key LIKE 'TEST%';

-- =====================================================
-- TEAM 4: Completed Epics (for Forecast Accuracy)
-- =====================================================

-- Epic 1: Completed 2 weeks ago (faster than planned)
INSERT INTO jira_issues (issue_key, issue_id, project_key, summary, status, issue_type, is_subtask, team_id,
    jira_created_at, started_at, done_at, original_estimate_seconds, time_spent_seconds,
    assignee_account_id, assignee_display_name, auto_score, created_at, updated_at)
VALUES
('TEST-100', 'test-100', 'TEST', 'Интеграция с платёжной системой', 'Готово', 'Epic', false, 4,
    NOW() - INTERVAL '60 days', NOW() - INTERVAL '55 days', NOW() - INTERVAL '14 days',
    288000, 259200, '60465e032b3f9a006a25baa7', 'Анна Кардакова', 85.0, NOW(), NOW());

-- Stories for Epic 1
INSERT INTO jira_issues (issue_key, issue_id, project_key, summary, status, issue_type, is_subtask, team_id, parent_key,
    jira_created_at, started_at, done_at, original_estimate_seconds, time_spent_seconds,
    assignee_account_id, assignee_display_name, auto_score, created_at, updated_at)
VALUES
('TEST-101', 'test-101', 'TEST', 'Анализ требований к интеграции', 'Готово', 'Story', false, 4, 'TEST-100',
    NOW() - INTERVAL '60 days', NOW() - INTERVAL '55 days', NOW() - INTERVAL '45 days',
    57600, 50400, '557058:0bf7607a-80c5-4aaf-9bb0-22f12379338b', 'test SA', 90.0, NOW(), NOW()),
('TEST-102', 'test-102', 'TEST', 'Разработка API интеграции', 'Готово', 'Story', false, 4, 'TEST-100',
    NOW() - INTERVAL '55 days', NOW() - INTERVAL '45 days', NOW() - INTERVAL '25 days',
    144000, 136800, '60465e032b3f9a006a25baa7', 'Анна Кардакова', 85.0, NOW(), NOW()),
('TEST-103', 'test-103', 'TEST', 'Тестирование платежей', 'Готово', 'Story', false, 4, 'TEST-100',
    NOW() - INTERVAL '30 days', NOW() - INTERVAL '25 days', NOW() - INTERVAL '14 days',
    86400, 72000, '712020:4ef2cfc1-9fcd-43ee-a4d9-d2dc43a39825', 'Vladislav', 80.0, NOW(), NOW());

-- Sub-tasks for stories (with daily time logging pattern)
INSERT INTO jira_issues (issue_key, issue_id, project_key, summary, status, issue_type, is_subtask, team_id, parent_key,
    jira_created_at, started_at, done_at, original_estimate_seconds, time_spent_seconds,
    assignee_account_id, assignee_display_name, created_at, updated_at)
VALUES
-- SA subtasks
('TEST-101-1', 'test-101-1', 'TEST', '[SA] Сбор требований от бизнеса', 'Готово', 'Sub-task', true, 4, 'TEST-101',
    NOW() - INTERVAL '60 days', NOW() - INTERVAL '55 days', NOW() - INTERVAL '50 days',
    28800, 25200, '557058:0bf7607a-80c5-4aaf-9bb0-22f12379338b', 'test SA', NOW(), NOW()),
('TEST-101-2', 'test-101-2', 'TEST', '[SA] Документирование API', 'Готово', 'Sub-task', true, 4, 'TEST-101',
    NOW() - INTERVAL '55 days', NOW() - INTERVAL '50 days', NOW() - INTERVAL '45 days',
    28800, 25200, '557058:0bf7607a-80c5-4aaf-9bb0-22f12379338b', 'test SA', NOW(), NOW()),
-- DEV subtasks
('TEST-102-1', 'test-102-1', 'TEST', '[DEV] Создание эндпоинтов', 'Готово', 'Sub-task', true, 4, 'TEST-102',
    NOW() - INTERVAL '45 days', NOW() - INTERVAL '44 days', NOW() - INTERVAL '35 days',
    72000, 68400, '60465e032b3f9a006a25baa7', 'Анна Кардакова', NOW(), NOW()),
('TEST-102-2', 'test-102-2', 'TEST', '[DEV] Обработка webhooks', 'Готово', 'Sub-task', true, 4, 'TEST-102',
    NOW() - INTERVAL '40 days', NOW() - INTERVAL '35 days', NOW() - INTERVAL '25 days',
    72000, 68400, '60465e032b3f9a006a25baa7', 'Анна Кардакова', NOW(), NOW()),
-- QA subtasks
('TEST-103-1', 'test-103-1', 'TEST', '[QA] Тест-кейсы для платежей', 'Готово', 'Sub-task', true, 4, 'TEST-103',
    NOW() - INTERVAL '30 days', NOW() - INTERVAL '25 days', NOW() - INTERVAL '20 days',
    43200, 36000, '712020:4ef2cfc1-9fcd-43ee-a4d9-d2dc43a39825', 'Vladislav', NOW(), NOW()),
('TEST-103-2', 'test-103-2', 'TEST', '[QA] E2E тестирование', 'Готово', 'Sub-task', true, 4, 'TEST-103',
    NOW() - INTERVAL '25 days', NOW() - INTERVAL '20 days', NOW() - INTERVAL '14 days',
    43200, 36000, '712020:4ef2cfc1-9fcd-43ee-a4d9-d2dc43a39825', 'Vladislav', NOW(), NOW());

-- Epic 2: Completed 1 month ago (slower than planned - LATE)
INSERT INTO jira_issues (issue_key, issue_id, project_key, summary, status, issue_type, is_subtask, team_id,
    jira_created_at, started_at, done_at, original_estimate_seconds, time_spent_seconds,
    assignee_account_id, assignee_display_name, auto_score, created_at, updated_at)
VALUES
('TEST-200', 'test-200', 'TEST', 'Система отчётов', 'Готово', 'Epic', false, 4,
    NOW() - INTERVAL '75 days', NOW() - INTERVAL '70 days', NOW() - INTERVAL '30 days',
    345600, 432000, '60465e032b3f9a006a25baa7', 'Анна Кардакова', 78.0, NOW(), NOW());

-- Stories for Epic 2
INSERT INTO jira_issues (issue_key, issue_id, project_key, summary, status, issue_type, is_subtask, team_id, parent_key,
    jira_created_at, started_at, done_at, original_estimate_seconds, time_spent_seconds,
    assignee_account_id, assignee_display_name, auto_score, created_at, updated_at)
VALUES
('TEST-201', 'test-201', 'TEST', 'Проектирование отчётов', 'Готово', 'Story', false, 4, 'TEST-200',
    NOW() - INTERVAL '75 days', NOW() - INTERVAL '70 days', NOW() - INTERVAL '60 days',
    72000, 86400, '557058:0bf7607a-80c5-4aaf-9bb0-22f12379338b', 'test SA', 82.0, NOW(), NOW()),
('TEST-202', 'test-202', 'TEST', 'Разработка генератора отчётов', 'Готово', 'Story', false, 4, 'TEST-200',
    NOW() - INTERVAL '65 days', NOW() - INTERVAL '60 days', NOW() - INTERVAL '40 days',
    172800, 216000, '60465e032b3f9a006a25baa7', 'Анна Кардакова', 75.0, NOW(), NOW()),
('TEST-203', 'test-203', 'TEST', 'Тестирование отчётов', 'Готово', 'Story', false, 4, 'TEST-200',
    NOW() - INTERVAL '45 days', NOW() - INTERVAL '40 days', NOW() - INTERVAL '30 days',
    100800, 129600, '712020:4ef2cfc1-9fcd-43ee-a4d9-d2dc43a39825', 'Vladislav', 70.0, NOW(), NOW());

-- Sub-tasks for Epic 2 stories
INSERT INTO jira_issues (issue_key, issue_id, project_key, summary, status, issue_type, is_subtask, team_id, parent_key,
    jira_created_at, started_at, done_at, original_estimate_seconds, time_spent_seconds,
    assignee_account_id, assignee_display_name, created_at, updated_at)
VALUES
('TEST-201-1', 'test-201-1', 'TEST', '[SA] Анализ требований к отчётам', 'Готово', 'Sub-task', true, 4, 'TEST-201',
    NOW() - INTERVAL '75 days', NOW() - INTERVAL '70 days', NOW() - INTERVAL '65 days',
    36000, 43200, '557058:0bf7607a-80c5-4aaf-9bb0-22f12379338b', 'test SA', NOW(), NOW()),
('TEST-201-2', 'test-201-2', 'TEST', '[SA] Макеты отчётов', 'Готово', 'Sub-task', true, 4, 'TEST-201',
    NOW() - INTERVAL '70 days', NOW() - INTERVAL '65 days', NOW() - INTERVAL '60 days',
    36000, 43200, '557058:0bf7607a-80c5-4aaf-9bb0-22f12379338b', 'test SA', NOW(), NOW()),
('TEST-202-1', 'test-202-1', 'TEST', '[DEV] Backend для отчётов', 'Готово', 'Sub-task', true, 4, 'TEST-202',
    NOW() - INTERVAL '60 days', NOW() - INTERVAL '58 days', NOW() - INTERVAL '48 days',
    86400, 108000, '60465e032b3f9a006a25baa7', 'Анна Кардакова', NOW(), NOW()),
('TEST-202-2', 'test-202-2', 'TEST', '[DEV] Frontend для отчётов', 'Готово', 'Sub-task', true, 4, 'TEST-202',
    NOW() - INTERVAL '52 days', NOW() - INTERVAL '48 days', NOW() - INTERVAL '40 days',
    86400, 108000, '60465e032b3f9a006a25baa7', 'Анна Кардакова', NOW(), NOW()),
('TEST-203-1', 'test-203-1', 'TEST', '[QA] Функциональное тестирование', 'Готово', 'Sub-task', true, 4, 'TEST-203',
    NOW() - INTERVAL '42 days', NOW() - INTERVAL '40 days', NOW() - INTERVAL '35 days',
    50400, 64800, '712020:4ef2cfc1-9fcd-43ee-a4d9-d2dc43a39825', 'Vladislav', NOW(), NOW()),
('TEST-203-2', 'test-203-2', 'TEST', '[QA] Регрессионное тестирование', 'Готово', 'Sub-task', true, 4, 'TEST-203',
    NOW() - INTERVAL '38 days', NOW() - INTERVAL '35 days', NOW() - INTERVAL '30 days',
    50400, 64800, '712020:4ef2cfc1-9fcd-43ee-a4d9-d2dc43a39825', 'Vladislav', NOW(), NOW());

-- Epic 3: Completed 3 weeks ago (on time)
INSERT INTO jira_issues (issue_key, issue_id, project_key, summary, status, issue_type, is_subtask, team_id,
    jira_created_at, started_at, done_at, original_estimate_seconds, time_spent_seconds,
    assignee_account_id, assignee_display_name, auto_score, created_at, updated_at)
VALUES
('TEST-300', 'test-300', 'TEST', 'Мобильное приложение v2', 'Готово', 'Epic', false, 4,
    NOW() - INTERVAL '50 days', NOW() - INTERVAL '45 days', NOW() - INTERVAL '21 days',
    259200, 252000, '60465e032b3f9a006a25baa7', 'Анна Кардакова', 92.0, NOW(), NOW());

-- Stories for Epic 3
INSERT INTO jira_issues (issue_key, issue_id, project_key, summary, status, issue_type, is_subtask, team_id, parent_key,
    jira_created_at, started_at, done_at, original_estimate_seconds, time_spent_seconds,
    assignee_account_id, assignee_display_name, auto_score, created_at, updated_at)
VALUES
('TEST-301', 'test-301', 'TEST', 'Дизайн нового UI', 'Готово', 'Story', false, 4, 'TEST-300',
    NOW() - INTERVAL '50 days', NOW() - INTERVAL '45 days', NOW() - INTERVAL '38 days',
    57600, 54000, '557058:0bf7607a-80c5-4aaf-9bb0-22f12379338b', 'test SA', 95.0, NOW(), NOW()),
('TEST-302', 'test-302', 'TEST', 'Реализация нового UI', 'Готово', 'Story', false, 4, 'TEST-300',
    NOW() - INTERVAL '42 days', NOW() - INTERVAL '38 days', NOW() - INTERVAL '28 days',
    129600, 126000, '60465e032b3f9a006a25baa7', 'Анна Кардакова', 90.0, NOW(), NOW()),
('TEST-303', 'test-303', 'TEST', 'QA мобильного приложения', 'Готово', 'Story', false, 4, 'TEST-300',
    NOW() - INTERVAL '32 days', NOW() - INTERVAL '28 days', NOW() - INTERVAL '21 days',
    72000, 72000, '712020:4ef2cfc1-9fcd-43ee-a4d9-d2dc43a39825', 'Vladislav', 88.0, NOW(), NOW());

-- Sub-tasks for Epic 3
INSERT INTO jira_issues (issue_key, issue_id, project_key, summary, status, issue_type, is_subtask, team_id, parent_key,
    jira_created_at, started_at, done_at, original_estimate_seconds, time_spent_seconds,
    assignee_account_id, assignee_display_name, created_at, updated_at)
VALUES
('TEST-301-1', 'test-301-1', 'TEST', '[SA] UX исследование', 'Готово', 'Sub-task', true, 4, 'TEST-301',
    NOW() - INTERVAL '50 days', NOW() - INTERVAL '45 days', NOW() - INTERVAL '41 days',
    28800, 27000, '557058:0bf7607a-80c5-4aaf-9bb0-22f12379338b', 'test SA', NOW(), NOW()),
('TEST-301-2', 'test-301-2', 'TEST', '[SA] Прототипирование', 'Готово', 'Sub-task', true, 4, 'TEST-301',
    NOW() - INTERVAL '45 days', NOW() - INTERVAL '41 days', NOW() - INTERVAL '38 days',
    28800, 27000, '557058:0bf7607a-80c5-4aaf-9bb0-22f12379338b', 'test SA', NOW(), NOW()),
('TEST-302-1', 'test-302-1', 'TEST', '[DEV] Новые компоненты', 'Готово', 'Sub-task', true, 4, 'TEST-302',
    NOW() - INTERVAL '38 days', NOW() - INTERVAL '37 days', NOW() - INTERVAL '32 days',
    64800, 63000, '60465e032b3f9a006a25baa7', 'Анна Кардакова', NOW(), NOW()),
('TEST-302-2', 'test-302-2', 'TEST', '[DEV] Рефакторинг навигации', 'Готово', 'Sub-task', true, 4, 'TEST-302',
    NOW() - INTERVAL '35 days', NOW() - INTERVAL '32 days', NOW() - INTERVAL '28 days',
    64800, 63000, '60465e032b3f9a006a25baa7', 'Анна Кардакова', NOW(), NOW()),
('TEST-303-1', 'test-303-1', 'TEST', '[QA] Смоук-тесты', 'Готово', 'Sub-task', true, 4, 'TEST-303',
    NOW() - INTERVAL '30 days', NOW() - INTERVAL '28 days', NOW() - INTERVAL '25 days',
    36000, 36000, '712020:4ef2cfc1-9fcd-43ee-a4d9-d2dc43a39825', 'Vladislav', NOW(), NOW()),
('TEST-303-2', 'test-303-2', 'TEST', '[QA] Полное тестирование', 'Готово', 'Sub-task', true, 4, 'TEST-303',
    NOW() - INTERVAL '28 days', NOW() - INTERVAL '25 days', NOW() - INTERVAL '21 days',
    36000, 36000, '712020:4ef2cfc1-9fcd-43ee-a4d9-d2dc43a39825', 'Vladislav', NOW(), NOW());

-- =====================================================
-- TEAM 4: In Progress Epic (current work)
-- =====================================================

INSERT INTO jira_issues (issue_key, issue_id, project_key, summary, status, issue_type, is_subtask, team_id,
    jira_created_at, started_at, original_estimate_seconds, time_spent_seconds,
    assignee_account_id, assignee_display_name, auto_score, created_at, updated_at)
VALUES
('TEST-400', 'test-400', 'TEST', 'Система аналитики', 'В разработке', 'Epic', false, 4,
    NOW() - INTERVAL '20 days', NOW() - INTERVAL '15 days',
    432000, 172800, '60465e032b3f9a006a25baa7', 'Анна Кардакова', 88.0, NOW(), NOW());

INSERT INTO jira_issues (issue_key, issue_id, project_key, summary, status, issue_type, is_subtask, team_id, parent_key,
    jira_created_at, started_at, done_at, original_estimate_seconds, time_spent_seconds,
    assignee_account_id, assignee_display_name, auto_score, created_at, updated_at)
VALUES
('TEST-401', 'test-401', 'TEST', 'Архитектура аналитики', 'Готово', 'Story', false, 4, 'TEST-400',
    NOW() - INTERVAL '20 days', NOW() - INTERVAL '15 days', NOW() - INTERVAL '10 days',
    72000, 64800, '557058:0bf7607a-80c5-4aaf-9bb0-22f12379338b', 'test SA', 92.0, NOW(), NOW()),
('TEST-402', 'test-402', 'TEST', 'Сбор метрик', 'В разработке', 'Story', false, 4, 'TEST-400',
    NOW() - INTERVAL '12 days', NOW() - INTERVAL '10 days', NULL,
    172800, 86400, '60465e032b3f9a006a25baa7', 'Анна Кардакова', 85.0, NOW(), NOW()),
('TEST-403', 'test-403', 'TEST', 'Визуализация данных', 'Новое', 'Story', false, 4, 'TEST-400',
    NOW() - INTERVAL '5 days', NULL, NULL,
    187200, 21600, '60465e032b3f9a006a25baa7', 'Анна Кардакова', 78.0, NOW(), NOW());

-- Sub-tasks for in-progress epic
INSERT INTO jira_issues (issue_key, issue_id, project_key, summary, status, issue_type, is_subtask, team_id, parent_key,
    jira_created_at, started_at, done_at, original_estimate_seconds, time_spent_seconds,
    assignee_account_id, assignee_display_name, created_at, updated_at)
VALUES
('TEST-401-1', 'test-401-1', 'TEST', '[SA] Требования к аналитике', 'Готово', 'Sub-task', true, 4, 'TEST-401',
    NOW() - INTERVAL '20 days', NOW() - INTERVAL '15 days', NOW() - INTERVAL '12 days',
    36000, 32400, '557058:0bf7607a-80c5-4aaf-9bb0-22f12379338b', 'test SA', NOW(), NOW()),
('TEST-401-2', 'test-401-2', 'TEST', '[SA] Дизайн системы', 'Готово', 'Sub-task', true, 4, 'TEST-401',
    NOW() - INTERVAL '15 days', NOW() - INTERVAL '12 days', NOW() - INTERVAL '10 days',
    36000, 32400, '557058:0bf7607a-80c5-4aaf-9bb0-22f12379338b', 'test SA', NOW(), NOW()),
('TEST-402-1', 'test-402-1', 'TEST', '[DEV] Event tracking', 'В разработке', 'Sub-task', true, 4, 'TEST-402',
    NOW() - INTERVAL '10 days', NOW() - INTERVAL '9 days', NULL,
    86400, 57600, '60465e032b3f9a006a25baa7', 'Анна Кардакова', NOW(), NOW()),
('TEST-402-2', 'test-402-2', 'TEST', '[DEV] Aggregation service', 'Новое', 'Sub-task', true, 4, 'TEST-402',
    NOW() - INTERVAL '8 days', NULL, NULL,
    86400, 28800, '60465e032b3f9a006a25baa7', 'Анна Кардакова', NOW(), NOW()),
('TEST-403-1', 'test-403-1', 'TEST', '[DEV] Графики и дашборды', 'Новое', 'Sub-task', true, 4, 'TEST-403',
    NOW() - INTERVAL '5 days', NULL, NULL,
    100800, 14400, '60465e032b3f9a006a25baa7', 'Анна Кардакова', NOW(), NOW()),
('TEST-403-2', 'test-403-2', 'TEST', '[QA] Тестирование аналитики', 'Новое', 'Sub-task', true, 4, 'TEST-403',
    NOW() - INTERVAL '3 days', NULL, NULL,
    86400, 7200, '712020:4ef2cfc1-9fcd-43ee-a4d9-d2dc43a39825', 'Vladislav', NOW(), NOW());

-- =====================================================
-- TEAM 3: Similar structure
-- =====================================================

-- Epic 1: Completed (early)
INSERT INTO jira_issues (issue_key, issue_id, project_key, summary, status, issue_type, is_subtask, team_id,
    jira_created_at, started_at, done_at, original_estimate_seconds, time_spent_seconds,
    assignee_account_id, assignee_display_name, auto_score, created_at, updated_at)
VALUES
('TEST-500', 'test-500', 'TEST', 'Авторизация через SSO', 'Готово', 'Epic', false, 3,
    NOW() - INTERVAL '45 days', NOW() - INTERVAL '40 days', NOW() - INTERVAL '18 days',
    201600, 172800, '70121:b40ff773-75a6-4521-b351-6b0114b87dd4', 'Kirill Reshetov', 90.0, NOW(), NOW());

INSERT INTO jira_issues (issue_key, issue_id, project_key, summary, status, issue_type, is_subtask, team_id, parent_key,
    jira_created_at, started_at, done_at, original_estimate_seconds, time_spent_seconds,
    assignee_account_id, assignee_display_name, auto_score, created_at, updated_at)
VALUES
('TEST-501', 'test-501', 'TEST', 'Интеграция с LDAP', 'Готово', 'Story', false, 3, 'TEST-500',
    NOW() - INTERVAL '45 days', NOW() - INTERVAL '40 days', NOW() - INTERVAL '30 days',
    100800, 86400, '712020:f7d73d50-0577-4580-94bc-5a8a6a10ddf8', 'Александр', 92.0, NOW(), NOW()),
('TEST-502', 'test-502', 'TEST', 'OAuth2 провайдеры', 'Готово', 'Story', false, 3, 'TEST-500',
    NOW() - INTERVAL '35 days', NOW() - INTERVAL '30 days', NOW() - INTERVAL '18 days',
    100800, 86400, '70121:b40ff773-75a6-4521-b351-6b0114b87dd4', 'Kirill Reshetov', 88.0, NOW(), NOW());

-- Sub-tasks for Team 3 Epic 1
INSERT INTO jira_issues (issue_key, issue_id, project_key, summary, status, issue_type, is_subtask, team_id, parent_key,
    jira_created_at, started_at, done_at, original_estimate_seconds, time_spent_seconds,
    assignee_account_id, assignee_display_name, created_at, updated_at)
VALUES
('TEST-501-1', 'test-501-1', 'TEST', '[SA] Анализ LDAP схемы', 'Готово', 'Sub-task', true, 3, 'TEST-501',
    NOW() - INTERVAL '45 days', NOW() - INTERVAL '40 days', NOW() - INTERVAL '35 days',
    50400, 43200, '712020:f7d73d50-0577-4580-94bc-5a8a6a10ddf8', 'Александр', NOW(), NOW()),
('TEST-501-2', 'test-501-2', 'TEST', '[DEV] LDAP коннектор', 'Готово', 'Sub-task', true, 3, 'TEST-501',
    NOW() - INTERVAL '38 days', NOW() - INTERVAL '35 days', NOW() - INTERVAL '30 days',
    50400, 43200, '70121:b40ff773-75a6-4521-b351-6b0114b87dd4', 'Kirill Reshetov', NOW(), NOW()),
('TEST-502-1', 'test-502-1', 'TEST', '[DEV] Google OAuth', 'Готово', 'Sub-task', true, 3, 'TEST-502',
    NOW() - INTERVAL '32 days', NOW() - INTERVAL '30 days', NOW() - INTERVAL '24 days',
    50400, 43200, '70121:b40ff773-75a6-4521-b351-6b0114b87dd4', 'Kirill Reshetov', NOW(), NOW()),
('TEST-502-2', 'test-502-2', 'TEST', '[QA] Тесты авторизации', 'Готово', 'Sub-task', true, 3, 'TEST-502',
    NOW() - INTERVAL '26 days', NOW() - INTERVAL '24 days', NOW() - INTERVAL '18 days',
    50400, 43200, '712020:c7f55b2b-0935-4fa4-9a94-7e9cc4f91c76', 'Елисей', NOW(), NOW());

-- Epic 2: Completed (late)
INSERT INTO jira_issues (issue_key, issue_id, project_key, summary, status, issue_type, is_subtask, team_id,
    jira_created_at, started_at, done_at, original_estimate_seconds, time_spent_seconds,
    assignee_account_id, assignee_display_name, auto_score, created_at, updated_at)
VALUES
('TEST-600', 'test-600', 'TEST', 'Кэширование данных', 'Готово', 'Epic', false, 3,
    NOW() - INTERVAL '55 days', NOW() - INTERVAL '50 days', NOW() - INTERVAL '25 days',
    172800, 216000, '70121:b40ff773-75a6-4521-b351-6b0114b87dd4', 'Kirill Reshetov', 75.0, NOW(), NOW());

INSERT INTO jira_issues (issue_key, issue_id, project_key, summary, status, issue_type, is_subtask, team_id, parent_key,
    jira_created_at, started_at, done_at, original_estimate_seconds, time_spent_seconds,
    assignee_account_id, assignee_display_name, auto_score, created_at, updated_at)
VALUES
('TEST-601', 'test-601', 'TEST', 'Redis интеграция', 'Готово', 'Story', false, 3, 'TEST-600',
    NOW() - INTERVAL '55 days', NOW() - INTERVAL '50 days', NOW() - INTERVAL '35 days',
    86400, 108000, '70121:b40ff773-75a6-4521-b351-6b0114b87dd4', 'Kirill Reshetov', 72.0, NOW(), NOW()),
('TEST-602', 'test-602', 'TEST', 'Инвалидация кэша', 'Готово', 'Story', false, 3, 'TEST-600',
    NOW() - INTERVAL '40 days', NOW() - INTERVAL '35 days', NOW() - INTERVAL '25 days',
    86400, 108000, '70121:b40ff773-75a6-4521-b351-6b0114b87dd4', 'Kirill Reshetov', 70.0, NOW(), NOW());

-- Sub-tasks for Team 3 Epic 2
INSERT INTO jira_issues (issue_key, issue_id, project_key, summary, status, issue_type, is_subtask, team_id, parent_key,
    jira_created_at, started_at, done_at, original_estimate_seconds, time_spent_seconds,
    assignee_account_id, assignee_display_name, created_at, updated_at)
VALUES
('TEST-601-1', 'test-601-1', 'TEST', '[SA] Стратегия кэширования', 'Готово', 'Sub-task', true, 3, 'TEST-601',
    NOW() - INTERVAL '55 days', NOW() - INTERVAL '50 days', NOW() - INTERVAL '45 days',
    28800, 36000, '712020:f7d73d50-0577-4580-94bc-5a8a6a10ddf8', 'Александр', NOW(), NOW()),
('TEST-601-2', 'test-601-2', 'TEST', '[DEV] Redis клиент', 'Готово', 'Sub-task', true, 3, 'TEST-601',
    NOW() - INTERVAL '48 days', NOW() - INTERVAL '45 days', NOW() - INTERVAL '35 days',
    57600, 72000, '70121:b40ff773-75a6-4521-b351-6b0114b87dd4', 'Kirill Reshetov', NOW(), NOW()),
('TEST-602-1', 'test-602-1', 'TEST', '[DEV] TTL и eviction', 'Готово', 'Sub-task', true, 3, 'TEST-602',
    NOW() - INTERVAL '38 days', NOW() - INTERVAL '35 days', NOW() - INTERVAL '28 days',
    43200, 54000, '70121:b40ff773-75a6-4521-b351-6b0114b87dd4', 'Kirill Reshetov', NOW(), NOW()),
('TEST-602-2', 'test-602-2', 'TEST', '[QA] Нагрузочное тестирование', 'Готово', 'Sub-task', true, 3, 'TEST-602',
    NOW() - INTERVAL '30 days', NOW() - INTERVAL '28 days', NOW() - INTERVAL '25 days',
    43200, 54000, '712020:c7f55b2b-0935-4fa4-9a94-7e9cc4f91c76', 'Елисей', NOW(), NOW());

-- =====================================================
-- STATUS CHANGELOG (for time-in-status metrics)
-- =====================================================

-- Team 4 Epic 1 transitions
INSERT INTO status_changelog (issue_key, issue_id, from_status, to_status, transitioned_at, time_in_previous_status_seconds)
VALUES
('TEST-100', 'test-100', NULL, 'Новое', NOW() - INTERVAL '60 days', NULL),
('TEST-100', 'test-100', 'Новое', 'В разработке', NOW() - INTERVAL '55 days', 432000),
('TEST-100', 'test-100', 'В разработке', 'На тестировании', NOW() - INTERVAL '25 days', 2592000),
('TEST-100', 'test-100', 'На тестировании', 'Готово', NOW() - INTERVAL '14 days', 950400),

('TEST-101', 'test-101', NULL, 'Новое', NOW() - INTERVAL '60 days', NULL),
('TEST-101', 'test-101', 'Новое', 'Анализ', NOW() - INTERVAL '55 days', 432000),
('TEST-101', 'test-101', 'Анализ', 'Готово', NOW() - INTERVAL '45 days', 864000),

('TEST-102', 'test-102', NULL, 'Новое', NOW() - INTERVAL '55 days', NULL),
('TEST-102', 'test-102', 'Новое', 'В разработке', NOW() - INTERVAL '45 days', 864000),
('TEST-102', 'test-102', 'В разработке', 'Готово', NOW() - INTERVAL '25 days', 1728000),

('TEST-103', 'test-103', NULL, 'Новое', NOW() - INTERVAL '30 days', NULL),
('TEST-103', 'test-103', 'Новое', 'На тестировании', NOW() - INTERVAL '25 days', 432000),
('TEST-103', 'test-103', 'На тестировании', 'Готово', NOW() - INTERVAL '14 days', 950400);

-- Team 4 Epic 2 transitions
INSERT INTO status_changelog (issue_key, issue_id, from_status, to_status, transitioned_at, time_in_previous_status_seconds)
VALUES
('TEST-200', 'test-200', NULL, 'Новое', NOW() - INTERVAL '75 days', NULL),
('TEST-200', 'test-200', 'Новое', 'В разработке', NOW() - INTERVAL '70 days', 432000),
('TEST-200', 'test-200', 'В разработке', 'На тестировании', NOW() - INTERVAL '40 days', 2592000),
('TEST-200', 'test-200', 'На тестировании', 'Готово', NOW() - INTERVAL '30 days', 864000),

('TEST-201', 'test-201', NULL, 'Новое', NOW() - INTERVAL '75 days', NULL),
('TEST-201', 'test-201', 'Новое', 'Анализ', NOW() - INTERVAL '70 days', 432000),
('TEST-201', 'test-201', 'Анализ', 'Готово', NOW() - INTERVAL '60 days', 864000),

('TEST-202', 'test-202', NULL, 'Новое', NOW() - INTERVAL '65 days', NULL),
('TEST-202', 'test-202', 'Новое', 'В разработке', NOW() - INTERVAL '60 days', 432000),
('TEST-202', 'test-202', 'В разработке', 'Готово', NOW() - INTERVAL '40 days', 1728000),

('TEST-203', 'test-203', NULL, 'Новое', NOW() - INTERVAL '45 days', NULL),
('TEST-203', 'test-203', 'Новое', 'На тестировании', NOW() - INTERVAL '40 days', 432000),
('TEST-203', 'test-203', 'На тестировании', 'Готово', NOW() - INTERVAL '30 days', 864000);

-- Team 4 Epic 3 transitions
INSERT INTO status_changelog (issue_key, issue_id, from_status, to_status, transitioned_at, time_in_previous_status_seconds)
VALUES
('TEST-300', 'test-300', NULL, 'Новое', NOW() - INTERVAL '50 days', NULL),
('TEST-300', 'test-300', 'Новое', 'В разработке', NOW() - INTERVAL '45 days', 432000),
('TEST-300', 'test-300', 'В разработке', 'На тестировании', NOW() - INTERVAL '28 days', 1468800),
('TEST-300', 'test-300', 'На тестировании', 'Готово', NOW() - INTERVAL '21 days', 604800);

-- Team 3 transitions
INSERT INTO status_changelog (issue_key, issue_id, from_status, to_status, transitioned_at, time_in_previous_status_seconds)
VALUES
('TEST-500', 'test-500', NULL, 'Новое', NOW() - INTERVAL '45 days', NULL),
('TEST-500', 'test-500', 'Новое', 'В разработке', NOW() - INTERVAL '40 days', 432000),
('TEST-500', 'test-500', 'В разработке', 'Готово', NOW() - INTERVAL '18 days', 1900800),

('TEST-600', 'test-600', NULL, 'Новое', NOW() - INTERVAL '55 days', NULL),
('TEST-600', 'test-600', 'Новое', 'В разработке', NOW() - INTERVAL '50 days', 432000),
('TEST-600', 'test-600', 'В разработке', 'Готово', NOW() - INTERVAL '25 days', 2160000);

-- =====================================================
-- FORECAST SNAPSHOTS (for Forecast Accuracy metrics)
-- =====================================================

-- Team 4 snapshot 60 days ago (initial forecast)
-- TEST-100: planned 55->10 days ago (45 days), actual 55->14 days ago (41 days) = EARLY
-- TEST-200: planned 70->35 days ago (35 days), actual 70->30 days ago (40 days) = LATE
-- TEST-300: planned 45->22 days ago (23 days), actual 45->21 days ago (24 days) = ON_TIME
INSERT INTO forecast_snapshots (team_id, snapshot_date, unified_planning_json, forecast_json, created_at)
VALUES
(4, (CURRENT_DATE - INTERVAL '60 days')::date,
('{"teamId": 4, "planningDate": null, "epics": [
  {"epicKey": "TEST-100", "summary": "Интеграция с платёжной системой", "autoScore": 85.0, "startDate": "' || (CURRENT_DATE - INTERVAL '55 days')::date || '", "endDate": "' || (CURRENT_DATE - INTERVAL '10 days')::date || '", "stories": [], "phaseAggregation": null, "status": "В разработке", "dueDate": null, "totalEstimateSeconds": 288000, "totalLoggedSeconds": 0, "progressPercent": 0, "roleProgress": null, "storiesTotal": 3, "storiesActive": 1},
  {"epicKey": "TEST-200", "summary": "Система отчётов", "autoScore": 78.0, "startDate": "' || (CURRENT_DATE - INTERVAL '70 days')::date || '", "endDate": "' || (CURRENT_DATE - INTERVAL '35 days')::date || '", "stories": [], "phaseAggregation": null, "status": "В разработке", "dueDate": null, "totalEstimateSeconds": 345600, "totalLoggedSeconds": 0, "progressPercent": 0, "roleProgress": null, "storiesTotal": 3, "storiesActive": 1},
  {"epicKey": "TEST-300", "summary": "Мобильное приложение v2", "autoScore": 92.0, "startDate": "' || (CURRENT_DATE - INTERVAL '45 days')::date || '", "endDate": "' || (CURRENT_DATE - INTERVAL '22 days')::date || '", "stories": [], "phaseAggregation": null, "status": "Новое", "dueDate": null, "totalEstimateSeconds": 259200, "totalLoggedSeconds": 0, "progressPercent": 0, "roleProgress": null, "storiesTotal": 3, "storiesActive": 0}
], "warnings": [], "assigneeUtilization": {}}')::jsonb,
'{"calculatedAt": null, "teamId": 4, "teamCapacity": {"saHoursPerDay": 6, "devHoursPerDay": 12, "qaHoursPerDay": 6}, "wipStatus": {"limit": 3, "current": 3, "exceeded": false, "sa": null, "dev": null, "qa": null}, "epics": []}'::jsonb,
NOW() - INTERVAL '60 days');

-- Team 4 snapshot 30 days ago
INSERT INTO forecast_snapshots (team_id, snapshot_date, unified_planning_json, forecast_json, created_at)
VALUES
(4, (CURRENT_DATE - INTERVAL '30 days')::date,
('{"teamId": 4, "planningDate": null, "epics": [
  {"epicKey": "TEST-100", "summary": "Интеграция с платёжной системой", "autoScore": 85.0, "startDate": "' || (CURRENT_DATE - INTERVAL '55 days')::date || '", "endDate": "' || (CURRENT_DATE - INTERVAL '10 days')::date || '", "stories": [], "phaseAggregation": null, "status": "На тестировании", "dueDate": null, "totalEstimateSeconds": 288000, "totalLoggedSeconds": 180000, "progressPercent": 65, "roleProgress": null, "storiesTotal": 3, "storiesActive": 1},
  {"epicKey": "TEST-300", "summary": "Мобильное приложение v2", "autoScore": 92.0, "startDate": "' || (CURRENT_DATE - INTERVAL '45 days')::date || '", "endDate": "' || (CURRENT_DATE - INTERVAL '22 days')::date || '", "stories": [], "phaseAggregation": null, "status": "В разработке", "dueDate": null, "totalEstimateSeconds": 259200, "totalLoggedSeconds": 150000, "progressPercent": 58, "roleProgress": null, "storiesTotal": 3, "storiesActive": 2},
  {"epicKey": "TEST-400", "summary": "Система аналитики", "autoScore": 88.0, "startDate": "' || (CURRENT_DATE - INTERVAL '15 days')::date || '", "endDate": "' || (CURRENT_DATE + INTERVAL '15 days')::date || '", "stories": [], "phaseAggregation": null, "status": "В разработке", "dueDate": null, "totalEstimateSeconds": 432000, "totalLoggedSeconds": 50000, "progressPercent": 12, "roleProgress": null, "storiesTotal": 3, "storiesActive": 1}
], "warnings": [], "assigneeUtilization": {}}')::jsonb,
'{"calculatedAt": null, "teamId": 4, "teamCapacity": {"saHoursPerDay": 6, "devHoursPerDay": 12, "qaHoursPerDay": 6}, "wipStatus": {"limit": 3, "current": 3, "exceeded": false, "sa": null, "dev": null, "qa": null}, "epics": []}'::jsonb,
NOW() - INTERVAL '30 days');

-- Team 3 snapshot 50 days ago
-- TEST-500: planned 40->20 days ago (20 days), actual 40->18 days ago (22 days) = EARLY
-- TEST-600: planned 50->30 days ago (20 days), actual 50->25 days ago (25 days) = LATE
INSERT INTO forecast_snapshots (team_id, snapshot_date, unified_planning_json, forecast_json, created_at)
VALUES
(3, (CURRENT_DATE - INTERVAL '50 days')::date,
('{"teamId": 3, "planningDate": null, "epics": [
  {"epicKey": "TEST-500", "summary": "Авторизация через SSO", "autoScore": 90.0, "startDate": "' || (CURRENT_DATE - INTERVAL '40 days')::date || '", "endDate": "' || (CURRENT_DATE - INTERVAL '20 days')::date || '", "stories": [], "phaseAggregation": null, "status": "В разработке", "dueDate": null, "totalEstimateSeconds": 201600, "totalLoggedSeconds": 50000, "progressPercent": 25, "roleProgress": null, "storiesTotal": 2, "storiesActive": 1},
  {"epicKey": "TEST-600", "summary": "Кэширование данных", "autoScore": 75.0, "startDate": "' || (CURRENT_DATE - INTERVAL '50 days')::date || '", "endDate": "' || (CURRENT_DATE - INTERVAL '30 days')::date || '", "stories": [], "phaseAggregation": null, "status": "В разработке", "dueDate": null, "totalEstimateSeconds": 172800, "totalLoggedSeconds": 30000, "progressPercent": 17, "roleProgress": null, "storiesTotal": 2, "storiesActive": 1}
], "warnings": [], "assigneeUtilization": {}}')::jsonb,
'{"calculatedAt": null, "teamId": 3, "teamCapacity": {"saHoursPerDay": 6, "devHoursPerDay": 8, "qaHoursPerDay": 4}, "wipStatus": {"limit": 3, "current": 2, "exceeded": false, "sa": null, "dev": null, "qa": null}, "epics": []}'::jsonb,
NOW() - INTERVAL '50 days');

-- Team 3 snapshot 25 days ago
INSERT INTO forecast_snapshots (team_id, snapshot_date, unified_planning_json, forecast_json, created_at)
VALUES
(3, (CURRENT_DATE - INTERVAL '25 days')::date,
('{"teamId": 3, "planningDate": null, "epics": [
  {"epicKey": "TEST-500", "summary": "Авторизация через SSO", "autoScore": 90.0, "startDate": "' || (CURRENT_DATE - INTERVAL '40 days')::date || '", "endDate": "' || (CURRENT_DATE - INTERVAL '20 days')::date || '", "stories": [], "phaseAggregation": null, "status": "На тестировании", "dueDate": null, "totalEstimateSeconds": 201600, "totalLoggedSeconds": 150000, "progressPercent": 75, "roleProgress": null, "storiesTotal": 2, "storiesActive": 1}
], "warnings": [], "assigneeUtilization": {}}')::jsonb,
'{"calculatedAt": null, "teamId": 3, "teamCapacity": {"saHoursPerDay": 6, "devHoursPerDay": 8, "qaHoursPerDay": 4}, "wipStatus": {"limit": 3, "current": 1, "exceeded": false, "sa": null, "dev": null, "qa": null}, "epics": []}'::jsonb,
NOW() - INTERVAL '25 days');
