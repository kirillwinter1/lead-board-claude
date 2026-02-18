-- Allow NULL board_category for unmapped (newly discovered) issue types
ALTER TABLE issue_type_mappings ALTER COLUMN board_category DROP NOT NULL;
