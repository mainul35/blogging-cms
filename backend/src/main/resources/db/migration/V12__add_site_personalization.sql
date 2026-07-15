-- Site-wide appearance, editable from Settings > Personalization. Applied by
-- the frontend root layout to every page (public and admin) via CSS custom
-- properties + attribute-selector overrides -- see frontend/app/globals.css.
ALTER TABLE site_settings
    ADD COLUMN theme         VARCHAR(10) NOT NULL DEFAULT 'system',  -- light | dark | system
    ADD COLUMN contrast      VARCHAR(10) NOT NULL DEFAULT 'normal',  -- normal | high
    ADD COLUMN font          VARCHAR(10) NOT NULL DEFAULT 'inter',   -- inter | serif | mono
    ADD COLUMN accent_color  VARCHAR(10) NOT NULL DEFAULT 'blue';    -- blue | green | purple | red | orange | pink
