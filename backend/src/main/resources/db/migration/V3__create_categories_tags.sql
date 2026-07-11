CREATE TABLE categories (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    slug VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE tags (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    slug VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE post_tags (
    post_id BIGINT NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    tag_id  BIGINT NOT NULL REFERENCES tags (id)  ON DELETE CASCADE,
    PRIMARY KEY (post_id, tag_id)
);

ALTER TABLE posts
    ADD CONSTRAINT fk_posts_category FOREIGN KEY (category_id) REFERENCES categories (id);

-- Seed categories
INSERT INTO categories (name, slug) VALUES
    ('Technology', 'technology'),
    ('Lifestyle',  'lifestyle'),
    ('Travel',     'travel');

-- Seed tags
INSERT INTO tags (name, slug) VALUES
    ('JavaScript',   'javascript'),
    ('React',        'react'),
    ('Spring Boot',  'spring-boot'),
    ('Tutorial',     'tutorial'),
    ('Open Source',  'open-source');
