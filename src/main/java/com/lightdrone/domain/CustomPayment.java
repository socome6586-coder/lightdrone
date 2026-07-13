package com.lightdrone.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * 맞춤결제(custom payment).
 * 관리자가 특정 구매자/기업을 위해 개별 결제 건을 만들고, 고객은 맞춤결제 페이지에서
 * 맞춤결제코드(구매자 전화번호 끝 4자리)로 본인 건을 찾아 토스페이먼츠로 결제한다.
 * 일반 상품/주문 파이프라인과 분리된 독립 결제 건이다.
 */
@Entity
@Table(name = "custom_payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomPayment extends BaseEntity {

    /** 결제 상태 */
    public enum Status { PENDING, PAID, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "custom_payment_seq")
    @SequenceGenerator(name = "custom_payment_seq", sequenceName = "custom_payment_seq", allocationSize = 1)
    private Long id;

    /** 상품명 = 구매자 성함 또는 기업명 (예: 홍길동, 라이트드론) */
    @Column(nullable = false, length = 200)
    private String name;

    /** 맞춤결제코드 = 구매자 전화번호 끝 4자리 */
    @Column(nullable = false, length = 10)
    private String code;

    /** 결제 금액 (원) */
    @Column(nullable = false)
    private Long price;

    /** 배송 방법: "택배" 또는 "직접수령" */
    @Column(name = "delivery_method", nullable = false, length = 20)
    @Builder.Default
    private String deliveryMethod = "택배";

    /** 대표 이미지 URL (미등록 시 기본 이미지 사용) */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private Status status = Status.PENDING;

    /** 결제를 시작한 로그인 회원 ID (비회원이면 null) — 회원 주문내역 표시용 */
    @Column(name = "member_id")
    private Long memberId;

    /** 토스 결제용 주문번호 (결제 시작 시 생성) */
    @Column(name = "order_number", length = 50)
    private String orderNumber;

    /** 토스 결제 키 (결제 완료 시 저장) */
    @Column(name = "payment_key", length = 200)
    private String paymentKey;

    @Column(name = "paid_at")
    private java.time.LocalDateTime paidAt;

    /** 기본 이미지 폴백 — 등록된 이미지가 없으면 /images/payment.png */
    @Transient
    public String getImageUrlOrDefault() {
        return (imageUrl != null && !imageUrl.isBlank()) ? imageUrl : "/images/payment.png";
    }

    /** 상태 — NULL(기존 행)이면 PENDING 으로 처리 */
    public Status getStatus() {
        return status != null ? status : Status.PENDING;
    }
}
