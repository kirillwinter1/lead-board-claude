-- F35: Project hierarchy support (PROJECT → EPIC → STORY → SUBTASK)

ALTER TABLE project_configurations
    ADD COLUMN epic_link_type VARCHAR(20) DEFAULT 'parent',
    ADD COLUMN epic_link_name VARCHAR(100);

ALTER TABLE jira_issues
    ADD COLUMN child_epic_keys TEXT[];
