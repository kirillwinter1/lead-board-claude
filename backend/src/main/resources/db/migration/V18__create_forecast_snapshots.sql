-- Forecast snapshots for historical timeline view
CREATE TABLE forecast_snapshots (
    id BIGSERIAL PRIMARY KEY,
    team_id BIGINT NOT NULL,
    snapshot_date DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- Unified planning data (JSON)
    unified_planning_json JSONB NOT NULL,

    -- Forecast data (JSON)
    forecast_json JSONB NOT NULL,

    CONSTRAINT fk_forecast_snapshots_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
);

-- Index for efficient querying by team and date
CREATE INDEX idx_forecast_snapshots_team_date ON forecast_snapshots(team_id, snapshot_date);

-- Unique constraint to prevent duplicate snapshots per day per team
CREATE UNIQUE INDEX idx_forecast_snapshots_unique ON forecast_snapshots(team_id, snapshot_date);

-- Index for JSONB queries if needed
CREATE INDEX idx_forecast_snapshots_planning_json ON forecast_snapshots USING GIN (unified_planning_json);

COMMENT ON TABLE forecast_snapshots IS 'Daily snapshots of forecast/planning data for historical timeline view';
COMMENT ON COLUMN forecast_snapshots.unified_planning_json IS 'JSON snapshot of UnifiedPlanningResult';
COMMENT ON COLUMN forecast_snapshots.forecast_json IS 'JSON snapshot of ForecastResponse';
