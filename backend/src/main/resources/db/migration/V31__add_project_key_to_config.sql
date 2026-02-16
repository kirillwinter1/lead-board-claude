-- Add project_key column to project_configurations for multi-project support
ALTER TABLE project_configurations ADD COLUMN project_key VARCHAR(50);

-- Unique partial index: only one config per project_key (NULL is allowed for default/unassigned)
CREATE UNIQUE INDEX idx_project_configurations_project_key
    ON project_configurations(project_key) WHERE project_key IS NOT NULL;
