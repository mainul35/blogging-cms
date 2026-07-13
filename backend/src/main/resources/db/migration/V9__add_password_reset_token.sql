ALTER TABLE users
    ADD COLUMN reset_token VARCHAR(255),
    ADD COLUMN reset_token_expires_at TIMESTAMP;

CREATE INDEX idx_users_reset_token ON users (reset_token);
