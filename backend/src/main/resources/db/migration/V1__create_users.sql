CREATE TABLE users (
    id         BIGSERIAL PRIMARY KEY,
    username   VARCHAR(50),
    email      VARCHAR(255) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    role       VARCHAR(20)  NOT NULL DEFAULT 'AUTHOR',
    created_at TIMESTAMP             DEFAULT CURRENT_TIMESTAMP
);

-- Default admin user (password: Admin@1234 — change before production)
INSERT INTO users (email, password, role)
VALUES ('admin@blog.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ADMIN');
