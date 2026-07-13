-- =====================================================================
-- V2: 토스페이먼츠 결제 취소·재조회(동기화) 안전성 보강용 컬럼 추가
--
--  배경: 운영은 ddl-auto=validate 이므로 엔티티에 필드를 추가하면 반드시
--        대응 마이그레이션이 있어야 부팅(매핑↔스키마 검증)이 통과한다.
--
--  추가 컬럼(모두 NULL 허용 — 기존 행에 영향 없음):
--   - paid_amount       : 토스 승인 API 가 확정한 실제 승인 금액(원). 취소·정산 대조 기준.
--   - cancelled_amount  : 토스 취소 API 로 취소된 누적 금액(원). 전체취소 시 paid_amount 와 동일.
--   - payment_status    : 토스 측 결제 상태 문자열(DONE/CANCELED/PARTIAL_CANCELED/ABORTED 등).
--                         사이트 주문상태와 별개로 '결제사 원장 상태'를 보관해 불일치 복구에 사용.
--
--  idempotent 하게 작성(IF NOT EXISTS)하여 재실행/부분적용 상황에서도 안전.
-- =====================================================================
ALTER TABLE orders ADD COLUMN IF NOT EXISTS paid_amount bigint;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cancelled_amount bigint;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_status character varying(30);
