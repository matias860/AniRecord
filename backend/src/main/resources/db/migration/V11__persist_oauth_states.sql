CREATE TABLE oauth_state (
    state_hash VARCHAR(64) PRIMARY KEY,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_oauth_state_expiry ON oauth_state(expires_at);
