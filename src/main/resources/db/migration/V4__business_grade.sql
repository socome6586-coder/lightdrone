-- =====================================================================
-- V4: 사업자 회원 등급 + 등급별 상시 할인율 전역 설정
--
--  배경: 운영은 ddl-auto=validate 이므로 엔티티에 필드/엔티티를 추가하면
--        반드시 대응 마이그레이션이 있어야 부팅(매핑↔스키마 검증)이 통과한다.
--
--  내용:
--   1) members.business_grade : 회원 등급(NONE/WHOLESALE/SUPER_WHOLESALE).
--      기존 회원은 모두 'NONE'(일반)으로 백필.
--   2) business_grade_policy  : 등급별 상시 할인율 전역 설정(단일 행 id=1).
--      기본값 도매 20%, 도도매 30%. 관리자가 조정 가능.
--
--  idempotent 하게 작성(IF NOT EXISTS)하여 재실행/부분적용 상황에서도 안전.
-- =====================================================================

-- 1) 회원 등급 컬럼
ALTER TABLE members ADD COLUMN IF NOT EXISTS business_grade character varying(30);
UPDATE members SET business_grade = 'NONE' WHERE business_grade IS NULL;
ALTER TABLE members ALTER COLUMN business_grade SET DEFAULT 'NONE';
ALTER TABLE members ALTER COLUMN business_grade SET NOT NULL;

-- 2) 등급별 할인율 전역 설정 테이블
CREATE TABLE IF NOT EXISTS business_grade_policy (
    id bigint NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    wholesale_percent integer NOT NULL,
    super_wholesale_percent integer NOT NULL,
    CONSTRAINT business_grade_policy_pkey PRIMARY KEY (id)
);

-- 단일 행(id=1) 시드 — 이미 있으면 건드리지 않음(관리자 조정값 보존)
INSERT INTO business_grade_policy (id, wholesale_percent, super_wholesale_percent)
VALUES (1, 20, 30)
ON CONFLICT (id) DO NOTHING;
