CREATE SEQUENCE IF NOT EXISTS public.support_video_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS support_videos (
    id bigint NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    title character varying(200) NOT NULL,
    youtube_url character varying(500) NOT NULL,
    category character varying(50),
    visible boolean NOT NULL DEFAULT true,
    sort_order integer NOT NULL DEFAULT 0,
    CONSTRAINT support_videos_pkey PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_support_videos_visible_sort
    ON support_videos (visible, sort_order, created_at DESC);
