-- =====================================================================
-- V1__baseline_schema.sql
-- 라이트드론 초기 스키마 (baseline)
--
-- 목적: 신규/빈 DB 에 전체 스키마를 생성한다.
--   - 기존 운영/로컬 DB(이미 Hibernate ddl-auto=update 로 생성됨)에는
--     flyway baseline-version=1 설정으로 이 마이그레이션이 적용되지 않는다.
--   - 따라서 기존 스키마는 절대 변경/삭제되지 않는다.
--
-- 생성 출처: 로컬 lightdrone-pg(PostgreSQL 16) 의 현재 스키마 덤프
--   (pg_dump --schema-only --no-owner --no-privileges) 를 정리한 것.
-- 운영 PostgreSQL 14 와도 호환되는 표준 DDL 만 포함.
-- =====================================================================

-- Name: cart_items; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.cart_items (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    quantity integer NOT NULL,
    selected_option_extra_price bigint,
    selected_option_id bigint,
    selected_option_name character varying(200),
    member_id bigint NOT NULL,
    product_id bigint NOT NULL
);

-- Name: cart_seq; Type: SEQUENCE; Schema: public; Owner: -

CREATE SEQUENCE public.cart_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- Name: chat_message_seq; Type: SEQUENCE; Schema: public; Owner: -

CREATE SEQUENCE public.chat_message_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- Name: chat_messages; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.chat_messages (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    content character varying(2000) NOT NULL,
    sender_role character varying(10) NOT NULL,
    room_id bigint NOT NULL,
    sender_id bigint NOT NULL,
    CONSTRAINT chat_messages_sender_role_check CHECK (((sender_role)::text = ANY ((ARRAY['USER'::character varying, 'ADMIN'::character varying])::text[])))
);

-- Name: chat_room_seq; Type: SEQUENCE; Schema: public; Owner: -

CREATE SEQUENCE public.chat_room_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- Name: chat_rooms; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.chat_rooms (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    last_message_at timestamp(6) without time zone,
    status character varying(20) NOT NULL,
    admin_id bigint,
    member_id bigint NOT NULL,
    CONSTRAINT chat_rooms_status_check CHECK (((status)::text = ANY ((ARRAY['WAITING'::character varying, 'ACTIVE'::character varying, 'CLOSED'::character varying])::text[])))
);

-- Name: custom_payment_seq; Type: SEQUENCE; Schema: public; Owner: -

CREATE SEQUENCE public.custom_payment_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- Name: custom_payments; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.custom_payments (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    code character varying(10) NOT NULL,
    delivery_method character varying(20) NOT NULL,
    image_url character varying(500),
    member_id bigint,
    name character varying(200) NOT NULL,
    order_number character varying(50),
    paid_at timestamp(6) without time zone,
    payment_key character varying(200),
    price bigint NOT NULL,
    status character varying(10) NOT NULL,
    CONSTRAINT custom_payments_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PAID'::character varying])::text[])))
);

-- Name: drone_services; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.drone_services (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    content text NOT NULL,
    icon character varying(50),
    image_path character varying(500),
    sort_order integer NOT NULL,
    summary character varying(500) NOT NULL,
    title character varying(100) NOT NULL,
    visible boolean NOT NULL
);

-- Name: home_panel_seq; Type: SEQUENCE; Schema: public; Owner: -

CREATE SEQUENCE public.home_panel_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- Name: home_panel_settings; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.home_panel_settings (
    id bigint NOT NULL,
    description character varying(300),
    image_url character varying(500),
    label character varying(100),
    link_url character varying(500),
    slot character varying(20) NOT NULL
);

-- Name: inquiries; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.inquiries (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    answer text,
    answered boolean NOT NULL,
    content text NOT NULL,
    email character varying(100) NOT NULL,
    name character varying(50) NOT NULL,
    phone character varying(20),
    subject character varying(200) NOT NULL,
    member_id bigint
);

-- Name: inquiry_seq; Type: SEQUENCE; Schema: public; Owner: -

CREATE SEQUENCE public.inquiry_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- Name: manual_seq; Type: SEQUENCE; Schema: public; Owner: -

CREATE SEQUENCE public.manual_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- Name: manuals; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.manuals (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    category character varying(50),
    content text NOT NULL,
    pinned boolean NOT NULL,
    title character varying(200) NOT NULL,
    view_count integer NOT NULL,
    member_id bigint
);

-- Name: member_seq; Type: SEQUENCE; Schema: public; Owner: -

CREATE SEQUENCE public.member_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- Name: members; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.members (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    address character varying(200),
    address_detail character varying(200),
    agree_outsourcing boolean NOT NULL,
    agree_privacy boolean NOT NULL,
    agree_third_party boolean NOT NULL,
    email character varying(100) NOT NULL,
    enabled boolean NOT NULL,
    name character varying(30) NOT NULL,
    password character varying(255) NOT NULL,
    phone character varying(20),
    provider character varying(20),
    provider_id character varying(200),
    role character varying(255) NOT NULL,
    username character varying(50) NOT NULL,
    zip_code character varying(10),
    CONSTRAINT members_role_check CHECK (((role)::text = ANY ((ARRAY['USER'::character varying, 'ADMIN'::character varying])::text[])))
);

-- Name: notice_seq; Type: SEQUENCE; Schema: public; Owner: -

CREATE SEQUENCE public.notice_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- Name: notices; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.notices (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    content text NOT NULL,
    image_url character varying(500),
    pinned boolean NOT NULL,
    title character varying(200) NOT NULL,
    view_count integer NOT NULL,
    member_id bigint
);

-- Name: order_item_seq; Type: SEQUENCE; Schema: public; Owner: -

CREATE SEQUENCE public.order_item_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- Name: order_items; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.order_items (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    product_name character varying(200) NOT NULL,
    product_price bigint NOT NULL,
    quantity integer NOT NULL,
    selected_option_name character varying(200),
    order_id bigint NOT NULL,
    product_id bigint
);

-- Name: order_seq; Type: SEQUENCE; Schema: public; Owner: -

CREATE SEQUENCE public.order_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- Name: orders; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.orders (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    address character varying(200),
    address_detail character varying(200),
    admin_memo character varying(500),
    buyer_email character varying(100),
    buyer_name character varying(30) NOT NULL,
    buyer_phone character varying(20) NOT NULL,
    courier_company character varying(50),
    delivery_message character varying(200),
    order_number character varying(30) NOT NULL,
    payment_key character varying(300),
    payment_method character varying(20) NOT NULL,
    receiver_name character varying(30) NOT NULL,
    receiver_phone character varying(20) NOT NULL,
    refund_note character varying(500),
    refund_reason character varying(500),
    refund_status character varying(20),
    reject_reason character varying(500),
    shipping_fee bigint,
    status character varying(20) NOT NULL,
    total_price bigint NOT NULL,
    tracking_number character varying(50),
    zip_code character varying(10),
    member_id bigint,
    CONSTRAINT orders_payment_method_check CHECK (((payment_method)::text = ANY ((ARRAY['BANK_TRANSFER'::character varying, 'CARD'::character varying])::text[]))),
    CONSTRAINT orders_refund_status_check CHECK (((refund_status)::text = ANY ((ARRAY['NONE'::character varying, 'REQUESTED'::character varying, 'PROCESSING'::character varying, 'COMPLETED'::character varying, 'REJECTED'::character varying])::text[]))),
    CONSTRAINT orders_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING_PAYMENT'::character varying, 'PAID'::character varying, 'PREPARING'::character varying, 'SHIPPING'::character varying, 'DELIVERED'::character varying, 'CANCELLED'::character varying, 'REJECTED'::character varying])::text[])))
);

-- Name: product_categories; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.product_categories (
    id bigint NOT NULL,
    display_order integer NOT NULL,
    name character varying(100) NOT NULL
);

-- Name: product_category_links; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.product_category_links (
    product_id bigint NOT NULL,
    category_name character varying(100)
);

-- Name: product_category_seq; Type: SEQUENCE; Schema: public; Owner: -

CREATE SEQUENCE public.product_category_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- Name: product_content_images; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.product_content_images (
    product_id bigint NOT NULL,
    image_url character varying(500),
    display_order integer NOT NULL
);

-- Name: product_main_images; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.product_main_images (
    product_id bigint NOT NULL,
    image_url character varying(500),
    display_order integer NOT NULL
);

-- Name: product_option_seq; Type: SEQUENCE; Schema: public; Owner: -

CREATE SEQUENCE public.product_option_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- Name: product_options; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.product_options (
    id bigint NOT NULL,
    extra_price bigint NOT NULL,
    group_name character varying(200),
    name character varying(200) NOT NULL,
    sort_order integer NOT NULL,
    product_id bigint NOT NULL
);

-- Name: product_seq; Type: SEQUENCE; Schema: public; Owner: -

CREATE SEQUENCE public.product_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- Name: product_subcategories; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.product_subcategories (
    id bigint NOT NULL,
    display_order integer NOT NULL,
    name character varying(100) NOT NULL,
    category_id bigint NOT NULL
);

-- Name: product_subcategory_seq; Type: SEQUENCE; Schema: public; Owner: -

CREATE SEQUENCE public.product_subcategory_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- Name: products; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.products (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    available boolean NOT NULL,
    category character varying(100),
    content_bg_color character varying(20),
    content_image_layout character varying(2000),
    description text,
    discount_percent integer,
    display_price bigint,
    free_shipping boolean NOT NULL,
    image_url character varying(255),
    manufacturer character varying(200),
    name character varying(200) NOT NULL,
    price bigint NOT NULL,
    price_on_request boolean NOT NULL,
    product_code character varying(100),
    sort_order integer NOT NULL,
    stock integer NOT NULL,
    subcategory character varying(100),
    video_url character varying(500),
    is_best boolean DEFAULT false,
    is_new boolean DEFAULT false
);

-- Name: qna; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.qna (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    answer text,
    answered boolean NOT NULL,
    category character varying(20),
    content text NOT NULL,
    image_url character varying(500),
    password character varying(100) NOT NULL,
    secret boolean NOT NULL,
    title character varying(200) NOT NULL,
    view_count integer NOT NULL,
    member_id bigint
);

-- Name: qna_images; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.qna_images (
    qna_id bigint NOT NULL,
    image_url character varying(500)
);

-- Name: qna_seq; Type: SEQUENCE; Schema: public; Owner: -

CREATE SEQUENCE public.qna_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- Name: review_images; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.review_images (
    review_id bigint NOT NULL,
    image_url character varying(500),
    sort_order integer NOT NULL
);

-- Name: review_seq; Type: SEQUENCE; Schema: public; Owner: -

CREATE SEQUENCE public.review_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- Name: reviews; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.reviews (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    admin_reply text,
    admin_reply_at timestamp(6) without time zone,
    category character varying(30) NOT NULL,
    content text NOT NULL,
    image_url character varying(500),
    product_name character varying(200),
    rating integer,
    title character varying(200) NOT NULL,
    view_count integer NOT NULL,
    visible boolean NOT NULL,
    member_id bigint,
    product_id bigint
);

-- Name: service_seq; Type: SEQUENCE; Schema: public; Owner: -

CREATE SEQUENCE public.service_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- Name: visitor_logs; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.visitor_logs (
    id bigint NOT NULL,
    ip_hash character varying(64) NOT NULL,
    visit_date date NOT NULL
);

-- Name: visitor_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: -

ALTER TABLE public.visitor_logs ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.visitor_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

-- Name: cart_items cart_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.cart_items
    ADD CONSTRAINT cart_items_pkey PRIMARY KEY (id);

-- Name: chat_messages chat_messages_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.chat_messages
    ADD CONSTRAINT chat_messages_pkey PRIMARY KEY (id);

-- Name: chat_rooms chat_rooms_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.chat_rooms
    ADD CONSTRAINT chat_rooms_pkey PRIMARY KEY (id);

-- Name: custom_payments custom_payments_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.custom_payments
    ADD CONSTRAINT custom_payments_pkey PRIMARY KEY (id);

-- Name: drone_services drone_services_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.drone_services
    ADD CONSTRAINT drone_services_pkey PRIMARY KEY (id);

-- Name: home_panel_settings home_panel_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.home_panel_settings
    ADD CONSTRAINT home_panel_settings_pkey PRIMARY KEY (id);

-- Name: inquiries inquiries_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.inquiries
    ADD CONSTRAINT inquiries_pkey PRIMARY KEY (id);

-- Name: manuals manuals_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.manuals
    ADD CONSTRAINT manuals_pkey PRIMARY KEY (id);

-- Name: members members_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.members
    ADD CONSTRAINT members_pkey PRIMARY KEY (id);

-- Name: notices notices_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.notices
    ADD CONSTRAINT notices_pkey PRIMARY KEY (id);

-- Name: order_items order_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT order_items_pkey PRIMARY KEY (id);

-- Name: orders orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_pkey PRIMARY KEY (id);

-- Name: product_categories product_categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.product_categories
    ADD CONSTRAINT product_categories_pkey PRIMARY KEY (id);

-- Name: product_content_images product_content_images_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.product_content_images
    ADD CONSTRAINT product_content_images_pkey PRIMARY KEY (product_id, display_order);

-- Name: product_main_images product_main_images_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.product_main_images
    ADD CONSTRAINT product_main_images_pkey PRIMARY KEY (product_id, display_order);

-- Name: product_options product_options_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.product_options
    ADD CONSTRAINT product_options_pkey PRIMARY KEY (id);

-- Name: product_subcategories product_subcategories_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.product_subcategories
    ADD CONSTRAINT product_subcategories_pkey PRIMARY KEY (id);

-- Name: products products_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_pkey PRIMARY KEY (id);

-- Name: qna qna_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.qna
    ADD CONSTRAINT qna_pkey PRIMARY KEY (id);

-- Name: review_images review_images_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.review_images
    ADD CONSTRAINT review_images_pkey PRIMARY KEY (review_id, sort_order);

-- Name: reviews reviews_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT reviews_pkey PRIMARY KEY (id);

-- Name: members uk9d30a9u1qpg8eou0otgkwrp5d; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.members
    ADD CONSTRAINT uk9d30a9u1qpg8eou0otgkwrp5d UNIQUE (email);

-- Name: visitor_logs ukd77ur76ckckma59whga1gtlgf; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.visitor_logs
    ADD CONSTRAINT ukd77ur76ckckma59whga1gtlgf UNIQUE (visit_date, ip_hash);

-- Name: product_categories ukfl075bwasjwsxybk4x174befx; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.product_categories
    ADD CONSTRAINT ukfl075bwasjwsxybk4x174befx UNIQUE (name);

-- Name: members uklj4daw762ura5d2y6iu7g5n1i; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.members
    ADD CONSTRAINT uklj4daw762ura5d2y6iu7g5n1i UNIQUE (username);

-- Name: home_panel_settings ukmy7td7rl3p9tfur7cbxulmud4; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.home_panel_settings
    ADD CONSTRAINT ukmy7td7rl3p9tfur7cbxulmud4 UNIQUE (slot);

-- Name: orders uknthkiu7pgmnqnu86i2jyoe2v7; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT uknthkiu7pgmnqnu86i2jyoe2v7 UNIQUE (order_number);

-- Name: visitor_logs visitor_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.visitor_logs
    ADD CONSTRAINT visitor_logs_pkey PRIMARY KEY (id);

-- Name: cart_items fk1re40cjegsfvw58xrkdp6bac6; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.cart_items
    ADD CONSTRAINT fk1re40cjegsfvw58xrkdp6bac6 FOREIGN KEY (product_id) REFERENCES public.products(id);

-- Name: product_content_images fk2u69n47thkqcpc8h3vei1qrui; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.product_content_images
    ADD CONSTRAINT fk2u69n47thkqcpc8h3vei1qrui FOREIGN KEY (product_id) REFERENCES public.products(id);

-- Name: orders fk2vq7lo4gkknrmghj3rqpqqg6s; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT fk2vq7lo4gkknrmghj3rqpqqg6s FOREIGN KEY (member_id) REFERENCES public.members(id);

-- Name: review_images fk3aayo5bjciyemf3bvvt987hkr; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.review_images
    ADD CONSTRAINT fk3aayo5bjciyemf3bvvt987hkr FOREIGN KEY (review_id) REFERENCES public.reviews(id);

-- Name: product_subcategories fk67cvn8uwl62e94g5mlw9ep37p; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.product_subcategories
    ADD CONSTRAINT fk67cvn8uwl62e94g5mlw9ep37p FOREIGN KEY (category_id) REFERENCES public.product_categories(id);

-- Name: reviews fk6wsc8rr8tb1fc782foh3mjc8q; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT fk6wsc8rr8tb1fc782foh3mjc8q FOREIGN KEY (member_id) REFERENCES public.members(id);

-- Name: product_options fk8vv4f8fru80wxocwgxwsrow61; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.product_options
    ADD CONSTRAINT fk8vv4f8fru80wxocwgxwsrow61 FOREIGN KEY (product_id) REFERENCES public.products(id);

-- Name: product_main_images fkaxnd1u018cw0y133etasw66qa; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.product_main_images
    ADD CONSTRAINT fkaxnd1u018cw0y133etasw66qa FOREIGN KEY (product_id) REFERENCES public.products(id);

-- Name: order_items fkbioxgbv59vetrxe0ejfubep1w; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT fkbioxgbv59vetrxe0ejfubep1w FOREIGN KEY (order_id) REFERENCES public.orders(id);

-- Name: inquiries fkbu0nxcd71x327o77d8n8a4ffn; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.inquiries
    ADD CONSTRAINT fkbu0nxcd71x327o77d8n8a4ffn FOREIGN KEY (member_id) REFERENCES public.members(id);

-- Name: manuals fkcn717rb4ga5ajoidc0i7fmofd; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.manuals
    ADD CONSTRAINT fkcn717rb4ga5ajoidc0i7fmofd FOREIGN KEY (member_id) REFERENCES public.members(id);

-- Name: notices fkfyxkep04c9d3qw0fbqh96hn3g; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.notices
    ADD CONSTRAINT fkfyxkep04c9d3qw0fbqh96hn3g FOREIGN KEY (member_id) REFERENCES public.members(id);

-- Name: chat_messages fkhalwepod3944695ji0suwoqb9; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.chat_messages
    ADD CONSTRAINT fkhalwepod3944695ji0suwoqb9 FOREIGN KEY (room_id) REFERENCES public.chat_rooms(id);

-- Name: qna_images fkim1xbr0o82mlm7q0grg8j334; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.qna_images
    ADD CONSTRAINT fkim1xbr0o82mlm7q0grg8j334 FOREIGN KEY (qna_id) REFERENCES public.qna(id);

-- Name: chat_messages fkmf86klrrgnufxig1bgb94kafu; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.chat_messages
    ADD CONSTRAINT fkmf86klrrgnufxig1bgb94kafu FOREIGN KEY (sender_id) REFERENCES public.members(id);

-- Name: cart_items fkmyrsw3rg5yxntcqv45s8q6msh; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.cart_items
    ADD CONSTRAINT fkmyrsw3rg5yxntcqv45s8q6msh FOREIGN KEY (member_id) REFERENCES public.members(id);

-- Name: order_items fkocimc7dtr037rh4ls4l95nlfi; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT fkocimc7dtr037rh4ls4l95nlfi FOREIGN KEY (product_id) REFERENCES public.products(id);

-- Name: qna fkorlict14p9fusa1h1x3x4owdd; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.qna
    ADD CONSTRAINT fkorlict14p9fusa1h1x3x4owdd FOREIGN KEY (member_id) REFERENCES public.members(id);

-- Name: reviews fkpl51cejpw4gy5swfar8br9ngi; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT fkpl51cejpw4gy5swfar8br9ngi FOREIGN KEY (product_id) REFERENCES public.products(id);

-- Name: chat_rooms fkr4g0322qrbska9s53str7pfmq; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.chat_rooms
    ADD CONSTRAINT fkr4g0322qrbska9s53str7pfmq FOREIGN KEY (admin_id) REFERENCES public.members(id);

-- Name: chat_rooms fksi5jj76g7owe55g7nkwhe1a6h; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.chat_rooms
    ADD CONSTRAINT fksi5jj76g7owe55g7nkwhe1a6h FOREIGN KEY (member_id) REFERENCES public.members(id);

-- Name: product_category_links fksy2a837crf1ceqni7fkwm1wwh; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.product_category_links
    ADD CONSTRAINT fksy2a837crf1ceqni7fkwm1wwh FOREIGN KEY (product_id) REFERENCES public.products(id);

