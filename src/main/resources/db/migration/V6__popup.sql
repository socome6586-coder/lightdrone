CREATE SEQUENCE IF NOT EXISTS public.popup_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS popups (
    id bigint NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    title character varying(100) NOT NULL,
    image_url character varying(500),
    link_url character varying(500),
    new_tab boolean NOT NULL DEFAULT true,
    active boolean NOT NULL DEFAULT true,
    start_at timestamp without time zone,
    end_at timestamp without time zone,
    width integer NOT NULL DEFAULT 420,
    sort_order integer NOT NULL DEFAULT 0,
    CONSTRAINT popups_pkey PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_popups_active ON popups (active, sort_order);
