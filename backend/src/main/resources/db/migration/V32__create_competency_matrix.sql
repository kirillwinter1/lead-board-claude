-- F35: Competency Matrix
CREATE TABLE member_competencies (
    id              BIGSERIAL PRIMARY KEY,
    team_member_id  BIGINT NOT NULL REFERENCES team_members(id) ON DELETE CASCADE,
    component_name  VARCHAR(200) NOT NULL,
    level           INTEGER NOT NULL DEFAULT 3,
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(team_member_id, component_name),
    CHECK(level >= 1 AND level <= 5)
);
CREATE INDEX idx_member_competencies_member ON member_competencies(team_member_id);

ALTER TABLE jira_issues ADD COLUMN components TEXT[];
CREATE INDEX idx_jira_issues_components ON jira_issues USING GIN(components);
