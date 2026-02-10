CREATE TABLE simulation_logs (
    id           BIGSERIAL PRIMARY KEY,
    team_id      BIGINT NOT NULL REFERENCES teams(id),
    sim_date     DATE NOT NULL,
    dry_run      BOOLEAN NOT NULL DEFAULT false,
    actions      JSONB NOT NULL DEFAULT '[]',
    summary      JSONB NOT NULL DEFAULT '{}',
    status       VARCHAR(50) NOT NULL DEFAULT 'RUNNING',
    error        TEXT,
    started_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at   TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_sim_logs_team_date ON simulation_logs(team_id, sim_date DESC);
