-- RICE Assessments: оценки проектов и эпиков

CREATE TABLE rice_assessments (
    id               BIGSERIAL PRIMARY KEY,
    issue_key        VARCHAR(50) NOT NULL UNIQUE,
    template_id      BIGINT NOT NULL REFERENCES rice_templates(id),
    assessed_by      BIGINT REFERENCES users(id),
    confidence       DECIMAL(3,2),
    effort_manual    VARCHAR(10),
    effort_auto      DECIMAL(10,2),
    total_reach      DECIMAL(10,2),
    total_impact     DECIMAL(10,2),
    effective_effort DECIMAL(10,2),
    rice_score       DECIMAL(10,2),
    normalized_score DECIMAL(5,2),
    created_at       TIMESTAMP DEFAULT NOW(),
    updated_at       TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_rice_assessments_issue ON rice_assessments(issue_key);

-- Выбранные ответы
CREATE TABLE rice_assessment_answers (
    id              BIGSERIAL PRIMARY KEY,
    assessment_id   BIGINT NOT NULL REFERENCES rice_assessments(id) ON DELETE CASCADE,
    criteria_id     BIGINT NOT NULL REFERENCES rice_criteria(id),
    option_id       BIGINT NOT NULL REFERENCES rice_criteria_options(id),
    score           DECIMAL(10,2) NOT NULL
);

CREATE INDEX idx_rice_answers_assessment ON rice_assessment_answers(assessment_id);
