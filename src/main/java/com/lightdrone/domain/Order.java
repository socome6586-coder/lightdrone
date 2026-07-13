package com.lightdrone.domain;

import com.lightdrone.domain.enums.OrderStatus;
import com.lightdrone.domain.enums.PaymentMethod;
import com.lightdrone.domain.enums.RefundStatus;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order extends BaseEntity implements Ownable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_seq")
    @SequenceGenerator(name = "order_seq", sequenceName = "order_seq", allocationSize = 1)
    private Long id;

    /** 주문번호 (예: ORD-20250515-000001) */
    @Column(unique = true, nullable = false, length = 30)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    /* 구매자 정보 */
    @Column(nullable = false, length = 30)
    private String buyerName;

    @Column(nullable = false, length = 20)
    private String buyerPhone;

    @Column(length = 100)
    private String buyerEmail;

    /* 배송 정보 */
    @Column(nullable = false, length = 30)
    private String receiverName;

    @Column(nullable = false, length = 20)
    private String receiverPhone;

    @Column(length = 10)
    private String zipCode;

    @Column(length = 200)
    private String address;

    @Column(length = 200)
    private String addressDetail;

    @Column(length = 200)
    private String deliveryMessage;

    @Column(nullable = false)
    private Long totalPrice;

    /** 기본 배송비(원) — 무료배송이 아닌 주문에 적용되는 배송비 기준값. 단일 출처. */
    public static final long DEFAULT_SHIPPING_FEE = 3_500L;

    /** 배송비 */
    @Column
    @Builder.Default
    private Long shippingFee = DEFAULT_SHIPPING_FEE;

    /** 관리자 메모 */
    @Column(length = 500)
    private String adminMemo;

    /** 주문 거절 사유 (고객에게 표시) */
    @Column(length = 500)
    private String rejectReason;

    /** 환불/반품 상태 */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private RefundStatus refundStatus = RefundStatus.NONE;

    /** 환불/반품 신청 사유 */
    @Column(length = 500)
    private String refundReason;

    /** 환불/반품 관리자 처리 메모 */
    @Column(length = 500)
    private String refundNote;

    /** 택배사 (예: CJ대한통운, 로젠택배 등) */
    @Column(length = 50)
    private String courierCompany;

    /** 운송장 번호 */
    @Column(length = 50)
    private String trackingNumber;

    /** 토스페이먼츠 결제 키 */
    @Column(length = 300)
    private String paymentKey;

    /** 토스 승인 API 가 확정한 실제 승인 금액(원). 결제 완료 시 저장 — 취소·정산 대조 기준. */
    @Column(name = "paid_amount")
    private Long paidAmount;

    /** 토스 취소 API 로 취소된 누적 금액(원). 전체취소 성공 시 paidAmount 와 동일하게 기록. */
    @Column(name = "cancelled_amount")
    private Long cancelledAmount;

    /**
     * 토스 측 결제 상태 문자열(DONE/CANCELED/PARTIAL_CANCELED/ABORTED 등).
     * 사이트 주문 상태(OrderStatus)와 별개로 '결제사 원장 상태'를 보관해
     * 관리자 재조회·동기화(불일치 복구)에 사용한다.
     */
    @Column(name = "payment_status", length = 30)
    private String paymentStatus;
}
