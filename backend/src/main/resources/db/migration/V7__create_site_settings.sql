-- Single-row table: always id=1. Holds site-wide branding editable from the
-- admin Settings page, instead of requiring an env var + redeploy.
CREATE TABLE site_settings (
    id         BIGINT PRIMARY KEY,
    site_name  VARCHAR(100) NOT NULL
);

INSERT INTO site_settings (id, site_name) VALUES (1, 'Blog CMS');
