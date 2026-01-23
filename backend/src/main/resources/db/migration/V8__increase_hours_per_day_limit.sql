-- Increase max hours_per_day from 12 to 24
ALTER TABLE team_members DROP CONSTRAINT IF EXISTS chk_hours_per_day;
ALTER TABLE team_members ADD CONSTRAINT chk_hours_per_day CHECK (hours_per_day > 0 AND hours_per_day <= 24);
