-- Add app_role column to users table for RBAC
ALTER TABLE users ADD COLUMN app_role VARCHAR(20) NOT NULL DEFAULT 'MEMBER';

-- Add CHECK constraint for valid roles
ALTER TABLE users ADD CONSTRAINT chk_app_role
    CHECK (app_role IN ('ADMIN', 'TEAM_LEAD', 'MEMBER', 'VIEWER'));

-- Set the first user (lowest id) as ADMIN
UPDATE users SET app_role = 'ADMIN' WHERE id = (SELECT MIN(id) FROM users);
