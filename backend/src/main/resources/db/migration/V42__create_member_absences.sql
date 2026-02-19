CREATE TABLE member_absences (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL REFERENCES team_members(id) ON DELETE CASCADE,
    absence_type VARCHAR(20) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    comment VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_absence_dates CHECK (end_date >= start_date),
    CONSTRAINT chk_absence_type CHECK (absence_type IN ('VACATION','SICK_LEAVE','DAY_OFF','OTHER'))
);

CREATE INDEX idx_member_absences_member ON member_absences(member_id);
CREATE INDEX idx_member_absences_dates ON member_absences(member_id, start_date, end_date);
