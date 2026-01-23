-- Teams table
CREATE TABLE teams (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    jira_team_value VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Team members table
CREATE TABLE team_members (
    id BIGSERIAL PRIMARY KEY,
    team_id BIGINT NOT NULL REFERENCES teams(id),
    jira_account_id VARCHAR(255) NOT NULL,
    display_name VARCHAR(255),
    role VARCHAR(50) NOT NULL DEFAULT 'DEV',
    grade VARCHAR(50) NOT NULL DEFAULT 'MIDDLE',
    hours_per_day DECIMAL(3,1) NOT NULL DEFAULT 6.0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_hours_per_day CHECK (hours_per_day > 0 AND hours_per_day <= 12),
    CONSTRAINT chk_role CHECK (role IN ('SA', 'DEV', 'QA')),
    CONSTRAINT chk_grade CHECK (grade IN ('JUNIOR', 'MIDDLE', 'SENIOR'))
);

-- Index for faster lookups
CREATE INDEX idx_team_members_team_id ON team_members(team_id);
CREATE INDEX idx_team_members_jira_account_id ON team_members(jira_account_id);

-- Unique constraint: one jira_account_id per team
CREATE UNIQUE INDEX idx_team_members_unique_account_per_team
    ON team_members(team_id, jira_account_id) WHERE active = TRUE;
