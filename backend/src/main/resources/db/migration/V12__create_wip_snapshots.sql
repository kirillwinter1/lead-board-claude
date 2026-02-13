-- WIP snapshots for history chart
CREATE TABLE wip_snapshots (
    id BIGSERIAL PRIMARY KEY,
    team_id BIGINT NOT NULL,
    snapshot_date DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- Team-level WIP
    team_wip_limit INTEGER NOT NULL,
    team_wip_current INTEGER NOT NULL,

    -- Dynamic role-level WIP (e.g. {"SA": {"limit": 3, "current": 2}, "DEV": {"limit": 5, "current": 4}})
    role_wip_data JSONB,

    -- Queue info
    epics_in_queue INTEGER,
    total_epics INTEGER,

    CONSTRAINT fk_wip_snapshots_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
);

-- Index for efficient querying by team and date range
CREATE INDEX idx_wip_snapshots_team_date ON wip_snapshots(team_id, snapshot_date);

-- Unique constraint to prevent duplicate snapshots per day
CREATE UNIQUE INDEX idx_wip_snapshots_unique ON wip_snapshots(team_id, snapshot_date);
