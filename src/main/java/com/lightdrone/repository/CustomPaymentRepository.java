package com.lightdrone.repository;

import com.lightdrone.domain.CustomPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomPaymentRepository extends JpaRepository<CustomPayment, Long> {

    /** 관리자 목록 — 최신 등록순 */
    List<CustomPayment> findAllByOrderByCreatedAtDesc();

    /** 고객 코드 조회 — 해당 코드의 모든 건(미결제·결제완료 포함), 최신순 */
    List<CustomPayment> findByCodeOrderByCreatedAtDesc(String code);

    /** 토스 결제 콜백에서 주문번호로 조회 */
    Optional<CustomPayment> findByOrderNumber(String orderNumber);

    /** 회원 주문내역 — 해당 회원이 결제(시작)한 맞춤결제, 최신순 */
    List<CustomPayment> findByMemberIdOrderByCreatedAtDesc(Long memberId);
}
