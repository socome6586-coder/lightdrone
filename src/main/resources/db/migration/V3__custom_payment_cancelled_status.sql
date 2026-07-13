-- 맞춤결제도 토스 전체취소 상태를 별도로 보존한다.
ALTER TABLE custom_payments DROP CONSTRAINT IF EXISTS custom_payments_status_check;
ALTER TABLE custom_payments
    ADD CONSTRAINT custom_payments_status_check
    CHECK (status IN ('PENDING', 'PAID', 'CANCELLED'));
