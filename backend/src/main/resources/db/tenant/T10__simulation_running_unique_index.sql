-- BUG-76: Prevent TOCTOU race condition with a partial unique index
-- Only one row with status='RUNNING' can exist at a time (per team)
CREATE UNIQUE INDEX IF NOT EXISTS idx_sim_logs_running ON simulation_logs(team_id) WHERE status = 'RUNNING';
