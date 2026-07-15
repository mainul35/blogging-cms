-- Single-row table (like site_settings): lets an admin configure the mail
-- gateway from Settings/the setup wizard instead of editing application.yml.
-- Secrets (smtp_password, *_api_key) are stored in plaintext here, matching
-- the bar already set by application.yml's own MAIL_SMTP_PASSWORD etc. env
-- vars -- proportionate for this project's scale, not a step down.
CREATE TABLE mail_settings (
    id               BIGINT PRIMARY KEY,
    provider         VARCHAR(20)  NOT NULL DEFAULT 'log',
    from_address     VARCHAR(255) NOT NULL DEFAULT 'noreply@example.com',
    reply_to         VARCHAR(255),
    smtp_host        VARCHAR(255),
    smtp_port        INT          NOT NULL DEFAULT 587,
    smtp_username    VARCHAR(255),
    smtp_password    VARCHAR(255),
    smtp_auth        BOOLEAN      NOT NULL DEFAULT TRUE,
    smtp_starttls    BOOLEAN      NOT NULL DEFAULT TRUE,
    resend_api_key   VARCHAR(255),
    sendgrid_api_key VARCHAR(255)
);

INSERT INTO mail_settings (id) VALUES (1);
