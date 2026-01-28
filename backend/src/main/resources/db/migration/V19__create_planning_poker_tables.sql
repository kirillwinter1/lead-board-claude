-- Planning Poker tables for team estimation sessions

-- Poker sessions (one session = one epic)
CREATE TABLE poker_sessions (
    id BIGSERIAL PRIMARY KEY,
    team_id BIGINT NOT NULL REFERENCES teams(id),
    epic_key VARCHAR(50) NOT NULL,
    facilitator_account_id VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PREPARING',
    room_code VARCHAR(10) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,

    CONSTRAINT chk_session_status CHECK (status IN ('PREPARING', 'ACTIVE', 'COMPLETED'))
);

-- Stories in poker session
CREATE TABLE poker_stories (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES poker_sessions(id) ON DELETE CASCADE,
    story_key VARCHAR(50),
    title VARCHAR(500) NOT NULL,
    needs_sa BOOLEAN NOT NULL DEFAULT false,
    needs_dev BOOLEAN NOT NULL DEFAULT false,
    needs_qa BOOLEAN NOT NULL DEFAULT false,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    final_sa_hours INTEGER,
    final_dev_hours INTEGER,
    final_qa_hours INTEGER,
    order_index INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_story_status CHECK (status IN ('PENDING', 'VOTING', 'REVEALED', 'COMPLETED'))
);

-- Participant votes
CREATE TABLE poker_votes (
    id BIGSERIAL PRIMARY KEY,
    story_id BIGINT NOT NULL REFERENCES poker_stories(id) ON DELETE CASCADE,
    voter_account_id VARCHAR(100) NOT NULL,
    voter_display_name VARCHAR(255),
    voter_role VARCHAR(20) NOT NULL,
    vote_hours INTEGER,
    voted_at TIMESTAMPTZ,

    CONSTRAINT chk_voter_role CHECK (voter_role IN ('SA', 'DEV', 'QA')),
    CONSTRAINT uq_vote_per_role UNIQUE(story_id, voter_account_id, voter_role)
);

-- Indexes
CREATE INDEX idx_poker_sessions_room ON poker_sessions(room_code);
CREATE INDEX idx_poker_sessions_epic ON poker_sessions(epic_key);
CREATE INDEX idx_poker_sessions_team ON poker_sessions(team_id);
CREATE INDEX idx_poker_stories_session ON poker_stories(session_id);
CREATE INDEX idx_poker_votes_story ON poker_votes(story_id);

COMMENT ON TABLE poker_sessions IS 'Planning Poker estimation sessions';
COMMENT ON TABLE poker_stories IS 'Stories being estimated in a poker session';
COMMENT ON TABLE poker_votes IS 'Individual votes from team members';
COMMENT ON COLUMN poker_sessions.room_code IS 'Short unique code for joining the room';
COMMENT ON COLUMN poker_votes.vote_hours IS 'NULL = not voted yet, -1 = ? (unknown)';
