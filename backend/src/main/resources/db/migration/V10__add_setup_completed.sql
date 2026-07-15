-- Drives the first-run setup wizard: false until the self-hoster configures
-- their own site name + admin identity, replacing the Flyway-seeded defaults.
ALTER TABLE site_settings
    ADD COLUMN setup_completed BOOLEAN NOT NULL DEFAULT FALSE;
