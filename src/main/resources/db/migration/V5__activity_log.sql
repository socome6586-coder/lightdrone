-- =====================================================================
-- V5: 관리자 활동(감사) 로그
--
--  배경: 운영은 ddl-auto=validate 이므로 ActivityLog 엔티티를 추가하면
--        반드시 대응 마이그레이션이 있어야 부팅(매핑↔스키마 검증)이 통과한다.
--
--  내용:
--   activity_logs : 관리자 콘솔에서 발생한 변경 작업(등록/수정/삭제/처리)을 기록.
--     - 누가(admin_username), 언제(created_at), 무엇을(menu/action),
--       어디로(request_uri/method), 어디서(ip_address) 했는지 남긴다.
--
--  idempotent 하게 작성(IF NOT EXISTS)하여 재실행/부분적용 상황에서도 안전.
-- =====================================================================

-- 시퀀스
CREATE SEQUENCE IF NOT EXISTS public.activity_log_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

-- 활동 로그 테이블
CREATE TABLE IF NOT EXISTS activity_logs (
    id bigint NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    admin_username character varying(50),
    menu character varying(40),
    action character varying(40),
    method character varying(10),
    request_uri character varying(300),
    description character varying(300),
    ip_address character varying(64),
    CONSTRAINT activity_logs_pkey PRIMARY KEY (id)
);

-- 최신순 조회 가속용 인덱스
CREATE INDEX IF NOT EXISTS idx_activity_logs_created_at ON activity_logs (created_at DESC);
