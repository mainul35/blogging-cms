CREATE TABLE posts (
    id              BIGSERIAL PRIMARY KEY,
    title           VARCHAR(255) NOT NULL,
    slug            VARCHAR(255) NOT NULL UNIQUE,
    excerpt         TEXT,
    content         TEXT         NOT NULL,
    cover_image_url VARCHAR(500),
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    author_id       BIGINT       NOT NULL REFERENCES users (id),
    category_id     BIGINT,
    created_at      TIMESTAMP             DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP             DEFAULT CURRENT_TIMESTAMP,
    published_at    TIMESTAMP
);

CREATE INDEX idx_posts_slug     ON posts (slug);
CREATE INDEX idx_posts_status   ON posts (status);
CREATE INDEX idx_posts_author   ON posts (author_id);
CREATE INDEX idx_posts_category ON posts (category_id);
