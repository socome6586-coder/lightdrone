ALTER TABLE product_categories
    ADD COLUMN IF NOT EXISTS home_banner_visible boolean NOT NULL DEFAULT true;
