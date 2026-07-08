-- F23 Planning Poker rework: story description + selected Jira component.
-- Description is collected in the "Add story" form and pushed to Jira on create;
-- jira_component is the component selected in the form (used for Jira create).
ALTER TABLE poker_stories ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE poker_stories ADD COLUMN IF NOT EXISTS jira_component VARCHAR(255);
