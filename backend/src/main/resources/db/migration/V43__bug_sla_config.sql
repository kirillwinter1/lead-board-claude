-- F42: Bug Management — SLA configuration table
CREATE TABLE bug_sla_config (
    id         BIGSERIAL PRIMARY KEY,
    priority   VARCHAR(50) NOT NULL UNIQUE,
    max_resolution_hours INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Default SLA thresholds by priority
INSERT INTO bug_sla_config (priority, max_resolution_hours) VALUES
    ('Highest', 24),
    ('High', 72),
    ('Medium', 168),
    ('Low', 336),
    ('Lowest', 672);
