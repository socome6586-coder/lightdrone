ALTER TABLE product_categories
    ADD COLUMN IF NOT EXISTS home_visible boolean NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS home_banner_image_url varchar(500),
    ADD COLUMN IF NOT EXISTS home_banner_title varchar(120),
    ADD COLUMN IF NOT EXISTS home_banner_subtitle varchar(300),
    ADD COLUMN IF NOT EXISTS home_banner_link_url varchar(500);
