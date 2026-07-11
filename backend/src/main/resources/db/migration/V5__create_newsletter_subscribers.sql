CREATE TABLE newsletter_subscribers (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    confirmed     BOOLEAN      NOT NULL DEFAULT false,
    token         VARCHAR(100) NOT NULL UNIQUE,   -- UUID used for double opt-in confirmation
    subscribed_at TIMESTAMP             DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_newsletter_token ON newsletter_subscribers (token);
CREATE INDEX idx_newsletter_email ON newsletter_subscribers (email);
