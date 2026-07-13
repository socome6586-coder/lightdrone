package com.lightdrone.service;

import com.lightdrone.domain.CartItem;
import com.lightdrone.domain.Member;
import com.lightdrone.domain.Order;
import com.lightdrone.domain.OrderItem;
import com.lightdrone.domain.enums.OrderStatus;
import com.lightdrone.domain.enums.PaymentMethod;
import com.lightdrone.domain.enums.RefundStatus;
import com.lightdrone.dto.OrderFormDto;
import com.lightdrone.repository.CartItemRepository;
import com.lightdrone.repository.OrderRepository;
import com.lightdrone.repository.ProductRepository;
import com.lightdrone.support.OwnershipUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    /** 배송비 기준값은 Order.DEFAULT_SHIPPING_FEE 에 단일 정의 — 여기서는 별칭으로 노출 */
    public static final long SHIPPING_FEE = Order.DEFAULT_SHIPPING_FEE;

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CartItemRepository cartItemRepository;
    private final CartService cartService;
    private final StockAlertService stockAlertService;
    private final TossPaymentService tossPaymentService;
    private final PricingService pricingService;

    /** 결제 승인 처리 결과 — 신규 승인인지(CONFIRMED), 이미 승인된 멱등 재요청인지(ALREADY_CONFIRMED). */
    public enum PaymentConfirmOutcome { CONFIRMED, ALREADY_CONFIRMED }

    /** 토스 원장과 사이트 주문 상태를 맞춘 결과(관리자 재조회·웹훅 동기화용). */
    public enum ReconcileOutcome {
        CONFIRMED_SYNCED, CANCEL_SYNCED, PARTIAL_CANCEL_DETECTED,
        AMOUNT_MISMATCH, NO_CHANGE, ORDER_NOT_FOUND
    }

    /** 바로 구매 */
    @Transactional
    public Order createDirect(Member member, Long productId, int quantity, OrderFormDto dto) {
        // 비관적 락(SELECT FOR UPDATE)으로 동시 주문 시 재고 Race Condition 방지
        var product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
        PurchasePolicy.validate(product, quantity);

        // 선택 옵션 조합 (옵션 제목별 1개씩) 검증 + 스냅샷
        CartService.OptionSelection sel = cartService.resolveOptions(product, dto.getOptionIds());

        Order order = buildOrder(member, dto);
        OrderItem item = OrderItem.builder()
                .order(order)
                .product(product)
                .productName(product.getName())
                // 사업자 회원 등급 상시 할인은 (상품 판매가 + 옵션 추가금)에 적용한다.
                .productPrice(pricingService.applyTo(product.getEffectivePrice() + sel.extraSum, member))
                .selectedOptionName(sel.combinedName)
                .quantity(quantity)
                .build();
        order.getItems().add(item);
        long shippingFee = product.isFreeShipping() ? 0L : SHIPPING_FEE;
        order.setShippingFee(shippingFee);
        order.setTotalPrice(item.getTotalPrice() + shippingFee);

        // 재고 차감
        int stockBefore = product.getStock();
        product.setStock(stockBefore - quantity);

        Order saved = orderRepository.save(order);
        // 품절/재고 부족 시 관리자 알림 (비동기 — 주문 처리에 영향 없음)
        stockAlertService.checkAfterDecrement(product.getName(), stockBefore, product.getStock());
        return saved;
    }

    /** 장바구니 전체 구매 */
    @Transactional
    public Order createFromCart(Member member, OrderFormDto dto) {
        var cartItems = cartItemRepository.findByMemberOrderByCreatedAtDesc(member);
        if (cartItems.isEmpty()) throw new IllegalArgumentException("장바구니가 비어있습니다.");

        Order order = buildOrder(member, dto);
        long total = 0L;
        // 재고 알림은 주문이 확정(save)된 뒤 한번에 처리 — 도중 롤백 시 거짓 알림 방지
        List<StockChange> stockChanges = new java.util.ArrayList<>();
        for (var ci : cartItems) {
            // 비관적 락으로 각 상품 행을 잠금 (동시 주문 Race Condition 방지)
            var product = productRepository.findByIdForUpdate(ci.getProduct().getId())
                    .orElseThrow(() -> new IllegalArgumentException("상품 정보를 찾을 수 없습니다."));
            PurchasePolicy.validate(product, ci.getQuantity());
            long optionExtra = (ci.getSelectedOptionExtraPrice() != null) ? ci.getSelectedOptionExtraPrice() : 0L;
            OrderItem item = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .productName(product.getName())
                    .productPrice(pricingService.applyTo(product.getEffectivePrice() + optionExtra, member))
                    .selectedOptionName(ci.getSelectedOptionName())
                    .quantity(ci.getQuantity())
                    .build();
            order.getItems().add(item);
            total += item.getTotalPrice();
            int stockBefore = product.getStock();
            product.setStock(stockBefore - ci.getQuantity());
            stockChanges.add(new StockChange(product.getName(), stockBefore, product.getStock()));
        }
        // 모든 상품이 무료배송이면 배송비 0원
        boolean allFree = cartItems.stream()
                .allMatch(ci -> ci.getProduct() != null && ci.getProduct().isFreeShipping());
        long shippingFee = allFree ? 0L : SHIPPING_FEE;
        order.setShippingFee(shippingFee);
        order.setTotalPrice(total + shippingFee);
        Order saved = orderRepository.save(order);

        // 장바구니 비우기
        cartItemRepository.deleteAllByMember(member);

        // 품절/재고 부족 시 관리자 알림 (비동기)
        for (StockChange sc : stockChanges) {
            stockAlertService.checkAfterDecrement(sc.name(), sc.before(), sc.after());
        }
        return saved;
    }

    /**
     * 비회원 세션 장바구니 전체 구매.
     * cartItems 는 GuestCartService.getCartItems(session) 가 만든 비영속 CartItem 목록이다.
     * 세션 장바구니 비우기는 호출 측(컨트롤러)에서 GuestCartService.clear(session) 로 처리한다.
     */
    @Transactional
    public Order createFromGuestCart(List<CartItem> cartItems, OrderFormDto dto) {
        if (cartItems == null || cartItems.isEmpty())
            throw new IllegalArgumentException("장바구니가 비어있습니다.");

        Order order = buildOrder(null, dto);
        long total = 0L;
        List<StockChange> stockChanges = new java.util.ArrayList<>();
        for (var ci : cartItems) {
            var product = productRepository.findByIdForUpdate(ci.getProduct().getId())
                    .orElseThrow(() -> new IllegalArgumentException("상품 정보를 찾을 수 없습니다."));
            PurchasePolicy.validate(product, ci.getQuantity());
            long optionExtra = (ci.getSelectedOptionExtraPrice() != null) ? ci.getSelectedOptionExtraPrice() : 0L;
            OrderItem item = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .productName(product.getName())
                    .productPrice(product.getEffectivePrice() + optionExtra)
                    .selectedOptionName(ci.getSelectedOptionName())
                    .quantity(ci.getQuantity())
                    .build();
            order.getItems().add(item);
            total += item.getTotalPrice();
            int stockBefore = product.getStock();
            product.setStock(stockBefore - ci.getQuantity());
            stockChanges.add(new StockChange(product.getName(), stockBefore, product.getStock()));
        }
        boolean allFree = cartItems.stream()
                .allMatch(ci -> ci.getProduct() != null && ci.getProduct().isFreeShipping());
        long shippingFee = allFree ? 0L : SHIPPING_FEE;
        order.setShippingFee(shippingFee);
        order.setTotalPrice(total + shippingFee);
        Order saved = orderRepository.save(order);

        for (StockChange sc : stockChanges) {
            stockAlertService.checkAfterDecrement(sc.name(), sc.before(), sc.after());
        }
        return saved;
    }

    /** createFromCart 에서 재고 알림을 주문 확정 후로 미루기 위한 스냅샷 */
    private record StockChange(String name, int before, int after) {}

    @Transactional(readOnly = true)
    public Order findByOrderNumber(String orderNumber) {
        return orderRepository.findByOrderNumberWithItems(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
    }

    public Order findById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
    }

    /** 주문 상세 조회 — items JOIN FETCH (OSIV off 환경에서 템플릿 지연로딩 방지) */
    @Transactional(readOnly = true)
    public Order findByIdWithItems(Long id) {
        return orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<Order> findByMember(Member member) {
        return orderRepository.findByMemberWithItems(member);
    }

    @Transactional(readOnly = true)
    public Page<Order> findAll(Pageable pageable) {
        return orderRepository.findAllWithItems(pageable);
    }

    /** CSV 내보내기용 — 전체 주문을 items 와 함께 최신순으로 로딩 */
    @Transactional(readOnly = true)
    public List<Order> findAllForExport() {
        return orderRepository.findAllWithItems(Pageable.unpaged()).getContent();
    }

    /** 통합검색용 — 주문번호·구매자명·연락처 부분일치 (최신순, limit 건) */
    @Transactional(readOnly = true)
    public List<Order> searchForAdmin(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) return List.of();
        return orderRepository.searchForAdmin(keyword.trim(), org.springframework.data.domain.PageRequest.of(0, limit));
    }

    /** 기간 매출 내보내기/통계용 — 취소 제외 주문을 기간으로 조회 (items 포함, 생성일 오름차순) */
    @Transactional(readOnly = true)
    public List<Order> findForSales(LocalDateTime start, LocalDateTime end) {
        return orderRepository.findSalesBetween(start, end);
    }

    public long countPendingPayment() {
        return orderRepository.countByStatus(OrderStatus.PENDING_PAYMENT);
    }

    public long sumTotalPrice() {
        return orderRepository.sumTotalPrice();
    }

    public long sumTodayPrice() {
        java.time.LocalDateTime start = java.time.LocalDate.now().atStartOfDay();
        java.time.LocalDateTime end = start.plusDays(1);
        return orderRepository.sumTotalPriceByDate(start, end);
    }

    public long countToday() {
        java.time.LocalDateTime start = java.time.LocalDate.now().atStartOfDay();
        java.time.LocalDateTime end = start.plusDays(1);
        return orderRepository.countByCreatedAtBetween(start, end);
    }

    public List<Order> getRecentAdminOrderAlerts() {
        LocalDateTime since = LocalDateTime.now().minusDays(14);
        return orderRepository.findAdminNotificationCandidates(since).stream()
                .filter(this::isAdminNotifiableOrder)
                .sorted(Comparator.comparing(this::adminOrderSortKey).reversed())
                .limit(5)
                .toList();
    }

    @Transactional
    public void updateStatus(Long orderId, OrderStatus status, String adminMemo,
                             String courierCompany, String trackingNumber) {
        Order order = findById(orderId);
        if (status == OrderStatus.CANCELLED || status == OrderStatus.REJECTED) {
            throw new IllegalArgumentException("취소와 거절은 전용 처리 버튼을 사용해주세요.");
        }
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.REJECTED) {
            throw new IllegalArgumentException("취소 또는 거절된 주문의 상태는 변경할 수 없습니다.");
        }
        order.setStatus(status);
        if (adminMemo != null) order.setAdminMemo(adminMemo);
        if (courierCompany != null && !courierCompany.isBlank()) order.setCourierCompany(courierCompany);
        if (trackingNumber != null && !trackingNumber.isBlank()) order.setTrackingNumber(trackingNumber);
    }

    @Transactional
    public void cancel(Long orderId) {
        Order order = findById(orderId);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("이미 취소된 주문입니다.");
        }
        if (order.getStatus() == OrderStatus.REJECTED) {
            throw new IllegalArgumentException("이미 거절된 주문입니다.");
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalArgumentException("결제 완료 이후 주문은 일반 취소할 수 없습니다. 환불 절차를 이용해주세요.");
        }
        restoreStock(order);
        order.setStatus(OrderStatus.CANCELLED);
    }

    /** 주문 항목 재고를 복원한다 (비관적 락으로 행 잠금 후 복원). */
    private void restoreStock(Order order) {
        for (var item : order.getItems()) {
            if (item.getProduct() != null) {
                productRepository.findByIdForUpdate(item.getProduct().getId())
                        .ifPresent(p -> p.setStock(p.getStock() + item.getQuantity()));
            }
        }
    }

    private boolean isAdminNotifiableOrder(Order order) {
        if (order.getPaymentMethod() == PaymentMethod.BANK_TRANSFER) {
            return order.getStatus() == OrderStatus.PENDING_PAYMENT;
        }
        return order.getPaymentMethod() == PaymentMethod.CARD
                && (order.getStatus() == OrderStatus.PAID
                || order.getStatus() == OrderStatus.PREPARING
                || order.getStatus() == OrderStatus.SHIPPING
                || order.getStatus() == OrderStatus.DELIVERED);
    }

    private LocalDateTime adminOrderSortKey(Order order) {
        if (order.getPaymentMethod() == PaymentMethod.CARD) {
            return order.getUpdatedAt() != null ? order.getUpdatedAt()
                    : order.getCreatedAt() != null ? order.getCreatedAt() : LocalDateTime.MIN;
        }
        return order.getCreatedAt() != null ? order.getCreatedAt() : LocalDateTime.MIN;
    }

    /** 환불/반품 신청 */
    @Transactional
    public void requestRefund(Long orderId, Long memberId, String reason) {
        Order order = findById(orderId);
        if (!OwnershipUtils.isOwner(order, memberId)) {
            throw new IllegalArgumentException("본인 주문만 신청할 수 있습니다.");
        }
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalArgumentException("배송 완료된 주문만 환불/반품 신청이 가능합니다.");
        }
        if (order.getRefundStatus() != RefundStatus.NONE) {
            throw new IllegalArgumentException("이미 환불/반품 신청된 주문입니다.");
        }
        order.setRefundStatus(RefundStatus.REQUESTED);
        order.setRefundReason(reason);
    }

    /** 관리자: 환불 상태 업데이트 */
    @Transactional
    public void updateRefundStatus(Long orderId, RefundStatus refundStatus, String refundNote) {
        Order order = findById(orderId);
        order.setRefundStatus(refundStatus);
        if (refundNote != null && !refundNote.isBlank()) order.setRefundNote(refundNote);
    }

    /** 주문 삭제 (취소·거절 상태만 허용) */
    @Transactional
    public void delete(Long orderId) {
        Order order = findById(orderId);
        if (order.getStatus() != OrderStatus.CANCELLED && order.getStatus() != OrderStatus.REJECTED) {
            throw new IllegalArgumentException("취소 또는 거절된 주문만 삭제할 수 있습니다.");
        }
        orderRepository.delete(order);
    }

    /** 일괄 삭제 (취소·거절 상태만) */
    @Transactional
    public int deleteAll(List<Long> orderIds) {
        int count = 0;
        for (Long id : orderIds) {
            try { delete(id); count++; } catch (IllegalArgumentException ignored) {}
        }
        return count;
    }

    @Transactional
    public void reject(Long orderId, String reason) {
        Order order = findById(orderId);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("이미 취소된 주문입니다.");
        }
        if (order.getStatus() == OrderStatus.REJECTED) {
            throw new IllegalArgumentException("이미 거절된 주문입니다.");
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalArgumentException("결제 완료 이후 주문은 거절할 수 없습니다. 환불 절차를 이용해주세요.");
        }
        restoreStock(order);
        order.setStatus(OrderStatus.REJECTED);
        order.setRejectReason(reason);
    }

    /**
     * 토스페이먼츠 결제 승인 후 상태 업데이트(승인 금액/상태 미기록 버전 — 하위호환).
     * 내부적으로 4-인자 버전에 위임한다.
     */
    @Transactional
    public PaymentConfirmOutcome confirmPayment(Long orderId, String paymentKey) {
        return confirmPayment(orderId, paymentKey, null, null);
    }

    /**
     * 토스페이먼츠 결제 승인 결과를 주문에 반영한다.
     *
     * <p><b>멱등성(요구 4)</b>: 동일 결제건의 콜백/새로고침이 중복 도달해도
     * 같은 paymentKey 로 이미 PAID 인 주문은 {@link PaymentConfirmOutcome#ALREADY_CONFIRMED}
     * 를 돌려주고 아무 부수효과(재고·SMS)도 일으키지 않는다. 호출 측은 이 값으로
     * '신규 승인일 때만' SMS/이메일을 보내 중복 발송을 막는다.
     *
     * <p><b>과판매 방지</b>: 미결제 자동취소(재고 복원)됐거나 다른 키로 이미 처리된
     * 주문이 뒤늦게 승인되는 경우는 거부한다.
     *
     * @param paidAmount    토스 승인 API 가 확정한 실제 승인 금액(없으면 미기록)
     * @param paymentStatus 토스 측 결제 상태 문자열(없으면 미기록)
     */
    @Transactional
    public PaymentConfirmOutcome confirmPayment(Long orderId, String paymentKey,
                                                Long paidAmount, String paymentStatus) {
        if (paymentKey == null || paymentKey.isBlank()) {
            throw new IllegalArgumentException("결제 키가 올바르지 않습니다.");
        }
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));

        if (paidAmount != null && !order.getTotalPrice().equals(paidAmount)) {
            throw new IllegalStateException("승인 금액이 주문 금액과 일치하지 않습니다.");
        }

        // 이미 결제 완료(또는 이후 단계)인 주문에 같은 키로 재요청 → 멱등 처리(중복 승인·SMS 방지)
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            if (paymentKey.equals(order.getPaymentKey())
                    && (order.getStatus() == OrderStatus.PAID
                    || order.getStatus() == OrderStatus.PREPARING
                    || order.getStatus() == OrderStatus.SHIPPING
                    || order.getStatus() == OrderStatus.DELIVERED)) {
                return PaymentConfirmOutcome.ALREADY_CONFIRMED;
            }
            // 미결제 자동취소된 주문이 뒤늦게 승인되거나, 다른 키로 처리된 경우는 거부
            throw new IllegalStateException("이미 처리되었거나 만료된 주문입니다. 고객센터로 문의해주세요.");
        }

        order.setStatus(OrderStatus.PAID);
        order.setPaymentKey(paymentKey);
        if (paidAmount != null) order.setPaidAmount(paidAmount);
        if (paymentStatus != null) order.setPaymentStatus(paymentStatus);
        return PaymentConfirmOutcome.CONFIRMED;
    }

    /**
     * 결제 완료 주문의 토스 전체취소(요구 5·6·7·8).
     *
     * <p>순서가 안전성의 핵심이다:
     * <ol>
     *   <li>주문 행을 비관적 락으로 잠그고 취소 가능 상태(PAID/PREPARING)인지 검증.</li>
     *   <li><b>토스 전체취소 API 를 먼저 호출</b>한다. 통신 오류/거절 시 예외가 전파되어
     *       주문 상태·재고는 <b>그대로</b> 유지된다(요구 7 — 결제사 취소 실패 시 사이트 미변경).</li>
     *   <li>토스가 CANCELED 이고 취소 가능 잔액이 0원임을 확인해 준 경우에만 재고를
     *       <b>정확히 한 번</b> 복원하고(요구 8) 주문을 CANCELLED 로 전환한다.</li>
     * </ol>
     *
     * @return 토스 취소 응답(취소 누적금액·상태 포함)
     */
    @Transactional
    public TossPaymentService.TossPaymentResult cancelPaidOrder(Long orderId, String reason) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));

        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.REJECTED) {
            throw new IllegalStateException("이미 취소·거절된 주문입니다.");
        }
        if (order.getStatus() != OrderStatus.PAID && order.getStatus() != OrderStatus.PREPARING) {
            throw new IllegalStateException("결제 완료(또는 상품 준비 중) 주문만 결제취소할 수 있습니다.");
        }
        if (order.getPaymentKey() == null || order.getPaymentKey().isBlank()) {
            throw new IllegalStateException("결제 키가 없는 주문은 결제취소할 수 없습니다. (무통장입금 등)");
        }

        // (1) 토스 전체취소 먼저 — 실패 시 예외 전파되어 아래 재고/상태 변경은 실행되지 않음
        TossPaymentService.TossPaymentResult result =
                tossPaymentService.cancelFull(order.getPaymentKey(), reason);

        // (2) 토스가 취소를 확인해 준 경우에만 사이트 반영
        if (!result.isFullyCanceled()) {
            throw new IllegalStateException("토스 취소가 확정되지 않았습니다: " + result.status());
        }

        restoreStock(order);
        order.setStatus(OrderStatus.CANCELLED);
        order.setPaymentStatus(result.status());
        // 취소 누적금액: 토스 balanceAmount 기반(전체취소면 paidAmount 와 동일)
        order.setCancelledAmount(result.canceledAmount() != null
                ? result.canceledAmount() : order.getTotalPrice());
        if (reason != null && !reason.isBlank()) {
            order.setAdminMemo("결제취소: " + reason);
        }
        return result;
    }

    /**
     * 토스 원장을 권위 있는 기준으로 사이트 주문 상태를 맞춘다(요구 12).
     *
     * <p>승인 성공 후 DB 저장 실패 등으로 결제사-사이트가 어긋났을 때, 관리자 재조회·웹훅이
     * 토스 조회 API 결과를 신뢰해 복구한다(웹훅 본문은 신뢰하지 않음 — 요구 10):
     * <ul>
     *   <li>토스 DONE + 사이트 PENDING_PAYMENT → 금액 일치 시 PAID 동기화(불일치면 AMOUNT_MISMATCH).</li>
     *   <li>토스 CANCELED(잔액 0원) + 사이트 PAID/PREPARING → 재고 한 번 복원 + CANCELLED.</li>
     *   <li>토스 PARTIAL_CANCELED → 취소 누적금액만 기록하고 주문/재고는 유지.</li>
     *   <li>그 외(이미 일치 등) → NO_CHANGE.</li>
     * </ul>
     */
    @Transactional
    public ReconcileOutcome reconcileWithToss(Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null) return ReconcileOutcome.ORDER_NOT_FOUND;

        // paymentKey 가 있으면 그것으로, 없으면 orderNumber 로 토스 원장 조회
        TossPaymentService.TossPaymentResult ledger =
                (order.getPaymentKey() != null && !order.getPaymentKey().isBlank())
                        ? tossPaymentService.getByPaymentKey(order.getPaymentKey())
                        : tossPaymentService.getByOrderId(order.getOrderNumber());

        order.setPaymentStatus(ledger.status());

        if (ledger.orderId() != null && !order.getOrderNumber().equals(ledger.orderId())) {
            return ReconcileOutcome.NO_CHANGE;
        }
        if (ledger.totalAmount() == null || !order.getTotalPrice().equals(ledger.totalAmount())) {
            return ReconcileOutcome.AMOUNT_MISMATCH;
        }

        // 부분취소는 주문 전체취소로 간주하지 않는다. 재고도 전체 복원하지 않는다.
        if (TossPaymentService.STATUS_PARTIAL_CANCELED.equals(ledger.status())) {
            order.setCancelledAmount(ledger.canceledAmount());
            return ReconcileOutcome.PARTIAL_CANCEL_DETECTED;
        }

        // 토스가 완전 취소 상태 → 사이트도 취소로 동기화(재고 한 번 복원)
        if (ledger.isFullyCanceled()) {
            if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.PREPARING) {
                restoreStock(order);
                order.setStatus(OrderStatus.CANCELLED);
                order.setCancelledAmount(ledger.canceledAmount());
                return ReconcileOutcome.CANCEL_SYNCED;
            }
            return ReconcileOutcome.NO_CHANGE;
        }

        // 토스가 승인 완료 → 사이트가 아직 미결제면 금액 대조 후 PAID 동기화
        if (ledger.isDone()) {
            if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                order.setStatus(OrderStatus.PAID);
                if (ledger.paymentKey() != null) order.setPaymentKey(ledger.paymentKey());
                order.setPaidAmount(ledger.totalAmount());
                return ReconcileOutcome.CONFIRMED_SYNCED;
            }
            return ReconcileOutcome.NO_CHANGE;
        }

        return ReconcileOutcome.NO_CHANGE;
    }

    /**
     * 웹훅용 — 토스 orderId(=사이트 orderNumber)로 주문을 찾아 토스 원장과 동기화한다(요구 10·12).
     * 주문을 못 찾으면 {@link ReconcileOutcome#ORDER_NOT_FOUND} 를 돌려준다(웹훅은 200 으로 응답).
     */
    @Transactional
    public ReconcileOutcome reconcileByOrderNumber(String orderNumber) {
        if (orderNumber == null || orderNumber.isBlank()) return ReconcileOutcome.ORDER_NOT_FOUND;
        Long id = orderRepository.findByOrderNumber(orderNumber).map(Order::getId).orElse(null);
        if (id == null) return ReconcileOutcome.ORDER_NOT_FOUND;
        return reconcileWithToss(id);
    }

    /**
     * 결제창을 닫는 등으로 성공/실패 콜백이 모두 오지 않아 방치된 카드결제 주문을
     * 일정 시간(분) 경과 후 자동 취소하고 재고를 복원한다.
     * 무통장입금 주문은 입금 대기 상태이므로 대상에서 제외.
     * @return 자동취소된 주문 수
     */
    @Transactional
    public int expireStalePendingPayments(int minutes) {
        java.time.LocalDateTime cutoff = java.time.LocalDateTime.now().minusMinutes(minutes);
        List<Order> stale = orderRepository.findStalePendingCardOrders(cutoff);
        for (Order order : stale) {
            restoreStock(order);
            order.setStatus(OrderStatus.CANCELLED);
            order.setAdminMemo("미결제 자동취소 (" + minutes + "분 경과, 결제 미완료)");
        }
        return stale.size();
    }

    /* 주문 공통 정보 세팅 */
    private Order buildOrder(Member member, OrderFormDto dto) {
        String orderNumber = "ORD-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return Order.builder()
                .orderNumber(orderNumber)
                .member(member)
                .paymentMethod(dto.getPaymentMethod())
                .buyerName(dto.getBuyerName())
                .buyerPhone(dto.getBuyerPhone())
                .buyerEmail(dto.getBuyerEmail())
                .receiverName(dto.getReceiverName())
                .receiverPhone(dto.getReceiverPhone())
                .zipCode(dto.getZipCode())
                .address(dto.getAddress())
                .addressDetail(dto.getAddressDetail())
                .deliveryMessage(dto.getDeliveryMessage())
                .totalPrice(0L)
                .build();
    }
}
