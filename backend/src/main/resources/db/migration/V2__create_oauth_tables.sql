-- Users table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    atlassian_account_id VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255),
    display_name VARCHAR(255),
    avatar_url VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_atlassian_account_id ON users(atlassian_account_id);

-- OAuth tokens table
CREATE TABLE oauth_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    access_token TEXT NOT NULL,
    refresh_token TEXT,
    token_type VARCHAR(50) DEFAULT 'Bearer',
    expires_at TIMESTAMP WITH TIME ZONE,
    scope VARCHAR(500),
    cloud_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_oauth_tokens_user_id UNIQUE (user_id)
);

CREATE INDEX idx_oauth_tokens_user_id ON oauth_tokens(user_id);
CREATE INDEX idx_oauth_tokens_expires_at ON oauth_tokens(expires_at);
