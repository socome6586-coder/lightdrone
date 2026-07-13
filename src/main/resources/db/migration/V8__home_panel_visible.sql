ALTER TABLE home_panel_settings
    ADD COLUMN IF NOT EXISTS visible boolean NOT NULL DEFAULT true;
