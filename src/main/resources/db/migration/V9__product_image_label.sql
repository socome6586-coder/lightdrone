-- 목록 카드 이미지 하단 라벨 (홈·products 목록 전용, 상세 페이지에는 미표시)
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS show_image_label boolean DEFAULT false;
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS image_label varchar(100);
