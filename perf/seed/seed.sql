-- seed.sql: Orchestrator — run all seed scripts in order
-- Usage: psql -d leadboard -f perf/seed/seed.sql

\echo '=== Perf Seed: Starting ==='
\echo ''

\echo '>>> Step 1: Cleanup existing perf data...'
\i cleanup.sql

\echo '>>> Step 2: Create tenants and users...'
\i 01_tenants_and_users.sql

\echo '>>> Step 3: Create tenant schemas...'
\i 02_tenant_schemas.sql

\echo '>>> Step 4: Seed workflow config...'
\i 03_workflow_config.sql

\echo '>>> Step 5: Seed teams and members...'
\i 04_teams_and_members.sql

\echo '>>> Step 6: Generate issues (this takes a while ~610K per tenant)...'
\i 05_issues.sql

\echo '>>> Step 7: Generate status changelog...'
\i 06_status_changelog.sql

\echo '>>> Step 8: Seed RICE and extras...'
\i 07_rice_and_extras.sql

\echo ''
\echo '=== Perf Seed: Complete ==='
\echo 'Tenants: perf-alpha, perf-beta, perf-gamma'
\echo 'Users per tenant: 100 (sessions: perf-session-{alpha,beta,gamma}-u001..u100)'
\echo 'Project keys per tenant: 10 (PERF-A through PERF-J)'
\echo 'Teams per tenant: 500 (50 per project key × 10 members each)'
\echo 'Issues per tenant: ~610,000 (10,000 epics + ~150,000 stories + ~450,000 subtasks)'
\echo 'Total: ~1,830,000 issues across 3 tenants'
