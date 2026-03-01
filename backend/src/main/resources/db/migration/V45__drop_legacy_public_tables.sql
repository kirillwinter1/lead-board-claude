-- V45: Drop legacy business tables from public schema
-- These tables now exist in tenant schemas (T1 migration).
-- Only shared tables remain in public: tenants, tenant_users, users, user_sessions, oauth_tokens, calendar_holidays

DROP TABLE IF EXISTS rice_assessment_answers CASCADE;
DROP TABLE IF EXISTS rice_assessments CASCADE;
DROP TABLE IF EXISTS rice_criteria_options CASCADE;
DROP TABLE IF EXISTS rice_criteria CASCADE;
DROP TABLE IF EXISTS rice_templates CASCADE;
DROP TABLE IF EXISTS member_absences CASCADE;
DROP TABLE IF EXISTS member_competencies CASCADE;
DROP TABLE IF EXISTS simulation_logs CASCADE;
DROP TABLE IF EXISTS poker_votes CASCADE;
DROP TABLE IF EXISTS poker_stories CASCADE;
DROP TABLE IF EXISTS poker_sessions CASCADE;
DROP TABLE IF EXISTS forecast_snapshots CASCADE;
DROP TABLE IF EXISTS wip_snapshots CASCADE;
DROP TABLE IF EXISTS flag_changelog CASCADE;
DROP TABLE IF EXISTS status_changelog CASCADE;
DROP TABLE IF EXISTS jira_issues CASCADE;
DROP TABLE IF EXISTS jira_sync_state CASCADE;
DROP TABLE IF EXISTS tracker_metadata_cache CASCADE;
DROP TABLE IF EXISTS link_type_mappings CASCADE;
DROP TABLE IF EXISTS status_mappings CASCADE;
DROP TABLE IF EXISTS issue_type_mappings CASCADE;
DROP TABLE IF EXISTS workflow_roles CASCADE;
DROP TABLE IF EXISTS team_members CASCADE;
DROP TABLE IF EXISTS teams CASCADE;
DROP TABLE IF EXISTS bug_sla_config CASCADE;
DROP TABLE IF EXISTS project_configurations CASCADE;
