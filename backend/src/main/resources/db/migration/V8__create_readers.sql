CREATE TABLE readers (
    id           BIGSERIAL PRIMARY KEY,
    handle       VARCHAR(50)  NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    email        VARCHAR(255) NOT NULL,
    avatar_url   VARCHAR(500),
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Same email via Google AND GitHub stays two distinct reader rows in this
-- phase (GitHub emails can be noreply-proxy addresses, so email-based
-- cross-provider merge would be unreliable) -- see plan doc for details.
CREATE TABLE reader_oauth_identities (
    id               BIGSERIAL PRIMARY KEY,
    reader_id        BIGINT NOT NULL REFERENCES readers (id) ON DELETE CASCADE,
    provider         VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    UNIQUE (provider, provider_user_id)
);

CREATE INDEX idx_reader_oauth_reader ON reader_oauth_identities (reader_id);

ALTER TABLE comments
    ADD COLUMN reader_id BIGINT REFERENCES readers (id) ON DELETE SET NULL;

CREATE INDEX idx_comments_reader ON comments (reader_id);

ALTER TABLE comment_mentions
    ADD COLUMN mentioned_reader_id BIGINT REFERENCES readers (id) ON DELETE CASCADE;

-- mentioned_user_id was NOT NULL originally; must relax now that a mention
-- can point at EITHER a reader OR the admin user, never both.
ALTER TABLE comment_mentions
    ALTER COLUMN mentioned_user_id DROP NOT NULL;

ALTER TABLE comment_mentions
    ADD CONSTRAINT chk_mention_target CHECK (
        (mentioned_user_id IS NOT NULL AND mentioned_reader_id IS NULL) OR
        (mentioned_user_id IS NULL AND mentioned_reader_id IS NOT NULL)
    );
