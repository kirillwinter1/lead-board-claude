-- RICE Scoring: шаблоны, подкритерии, варианты ответов

-- Шаблоны RICE (Business, Technical)
CREATE TABLE rice_templates (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(100) NOT NULL,
    code             VARCHAR(50) NOT NULL UNIQUE,
    strategic_weight DECIMAL(3,2) DEFAULT 1.0,
    active           BOOLEAN DEFAULT TRUE,
    created_at       TIMESTAMP DEFAULT NOW(),
    updated_at       TIMESTAMP DEFAULT NOW()
);

-- Подкритерии (R, I, C, E)
CREATE TABLE rice_criteria (
    id              BIGSERIAL PRIMARY KEY,
    template_id     BIGINT NOT NULL REFERENCES rice_templates(id),
    parameter       VARCHAR(10) NOT NULL,
    name            VARCHAR(300) NOT NULL,
    description     TEXT,
    selection_type  VARCHAR(20) NOT NULL,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    active          BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_rice_criteria_template ON rice_criteria(template_id);

-- Варианты ответов для подкритериев
CREATE TABLE rice_criteria_options (
    id              BIGSERIAL PRIMARY KEY,
    criteria_id     BIGINT NOT NULL REFERENCES rice_criteria(id) ON DELETE CASCADE,
    label           VARCHAR(500) NOT NULL,
    description     TEXT,
    score           DECIMAL(10,2) NOT NULL,
    sort_order      INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_rice_options_criteria ON rice_criteria_options(criteria_id);
