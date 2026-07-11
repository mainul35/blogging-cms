CREATE TABLE comments (
    id           BIGSERIAL PRIMARY KEY,
    post_id      BIGINT       NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    author_id    BIGINT                REFERENCES users (id) ON DELETE SET NULL,
    author_name  VARCHAR(100) NOT NULL,
    author_email VARCHAR(255),
    body         TEXT         NOT NULL,
    parent_id    BIGINT                REFERENCES comments (id) ON DELETE CASCADE,
    created_at   TIMESTAMP             DEFAULT CURRENT_TIMESTAMP
);

-- Tracks which users were @mentioned in each comment (surrogate PK for R2DBC simplicity)
CREATE TABLE comment_mentions (
    id                BIGSERIAL PRIMARY KEY,
    comment_id        BIGINT NOT NULL REFERENCES comments (id) ON DELETE CASCADE,
    mentioned_user_id BIGINT NOT NULL REFERENCES users (id)    ON DELETE CASCADE,
    UNIQUE (comment_id, mentioned_user_id)
);

CREATE INDEX idx_comments_post    ON comments (post_id);
CREATE INDEX idx_comments_parent  ON comments (parent_id);
CREATE INDEX idx_mentions_comment ON comment_mentions (comment_id);
