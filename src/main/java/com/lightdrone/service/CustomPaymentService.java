package com.lightdrone.service;

import com.lightdrone.domain.CustomPayment;
import com.lightdrone.dto.CustomPaymentDto;
import com.lightdrone.repository.CustomPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomPaymentService {

    private final CustomPaymentRepository customPaymentRepository;
    private final FileStorageService fileStorageService;
    private final TossPaymentService tossPaymentService;

    public enum ReconcileOutcome {
        CONFIRMED_SYNCED, CANCEL_SYNCED, PARTIAL_CANCEL_DETECTED,
        AMOUNT_MISMATCH, NO_CHANGE, PAYMENT_NOT_FOUND
    }

    // ─── 관리자 ────────────────────────────────────────────────

    public List<CustomPayment> findAllForAdmin() {
        return customPaymentRepository.findAllByOrderByCreatedAtDesc();
    }

    public CustomPayment findById(Long id) {
        return customPaymentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("맞춤결제 건을 찾을 수 없습니다."));
    }

    @Transactional
    public void create(CustomPaymentDto dto) {
        CustomPayment cp = CustomPayment.builder()
                .name(dto.getName())
                .code(dto.getCode())
                .price(dto.getPrice())
                .deliveryMethod(dto.getDeliveryMethod())
                .imageUrl(uploadIfPresent(dto.getImageFile(), null))
                .status(CustomPayment.Status.PENDING)
                .build();
        customPaymentRepository.save(cp);
    }

    @Transactional
    public void update(Long id, CustomPaymentDto dto) {
        CustomPayment cp = findById(id);
        cp.setName(dto.getName());
        cp.setCode(dto.getCode());
        cp.setPrice(dto.getPrice());
        cp.setDeliveryMethod(dto.getDeliveryMethod());
        cp.setImageUrl(uploadIfPresent(dto.getImageFile(), cp.getImageUrl()));
    }

    @Transactional
    public void delete(Long id) {
        CustomPayment cp = findById(id);
        if (cp.getImageUrl() != null && !cp.getImageUrl().isBlank()) {
            fileStorageService.delete(cp.getImageUrl());
        }
        customPaymentRepository.delete(cp);
    }

    // ─── 고객(결제) ────────────────────────────────────────────

    /** 코드로 맞춤결제 건 조회 (미결제·결제완료 모두 — 결제완료 건은 리본으로 표시) */
    public List<CustomPayment> findByCode(String code) {
        if (code == null || code.isBlank()) return List.of();
        return customPaymentRepository.findByCodeOrderByCreatedAtDesc(code.trim());
    }

    /** 관리자: 결제 상태 수동 변경 (결제완료/미결제) */
    @Transactional
    public void adminSetStatus(Long id, CustomPayment.Status status) {
        CustomPayment cp = findById(id);
        cp.setStatus(status);
        if (status == CustomPayment.Status.PAID) {
            if (cp.getPaidAt() == null) cp.setPaidAt(java.time.LocalDateTime.now());
        } else {
            cp.setPaidAt(null);
            cp.setPaymentKey(null);
        }
    }

    public CustomPayment findByOrderNumber(String orderNumber) {
        return customPaymentRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("맞춤결제 정보를 찾을 수 없습니다."));
    }

    /** 회원 주문내역용 — 해당 회원이 결제(시작)한 맞춤결제 목록 */
    public List<CustomPayment> findByMemberId(Long memberId) {
        if (memberId == null) return List.of();
        return customPaymentRepository.findByMemberIdOrderByCreatedAtDesc(memberId);
    }

    /**
     * 결제 시작 — 토스 결제용 주문번호를 생성·저장하고 반환한다.
     * 로그인 회원이면 memberId를 연결해 회원 주문내역에 표시되게 한다.
     * 이미 결제된 건이면 예외.
     */
    @Transactional
    public CustomPayment prepareForPayment(Long id, Long memberId) {
        CustomPayment cp = findById(id);
        if (cp.getStatus() != CustomPayment.Status.PENDING) {
            throw new IllegalStateException("결제 대기 상태인 건만 결제할 수 있습니다.");
        }
        if (memberId != null) cp.setMemberId(memberId);
        String orderNumber = "CPAY-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        cp.setOrderNumber(orderNumber);
        return cp;
    }

    /**
     * 결제 승인 후 상태를 PAID로 변경한다.
     *
     * <p>멱등성: 콜백 중복/새로고침으로 같은 paymentKey 가 다시 도달하면 예외 없이
     * {@code false}(이미 처리됨)를 돌려준다. 다른 키로 이미 PAID 인 건만 예외.
     *
     * @return 이번 호출로 새로 PAID 가 됐으면 true, 이미 같은 키로 처리됐으면 false
     */
    @Transactional
    public boolean markPaid(String orderNumber, String paymentKey) {
        CustomPayment cp = findByOrderNumber(orderNumber);
        if (cp.getStatus() == CustomPayment.Status.PAID) {
            if (paymentKey != null && paymentKey.equals(cp.getPaymentKey())) {
                return false; // 동일 결제건 재요청 — 멱등 처리
            }
            throw new IllegalStateException("이미 처리된 결제입니다.");
        }
        cp.setStatus(CustomPayment.Status.PAID);
        cp.setPaymentKey(paymentKey);
        cp.setPaidAt(java.time.LocalDateTime.now());
        return true;
    }

    /** 맞춤결제 전체취소. 토스가 CANCELED/잔액 0원을 반환한 뒤에만 상태를 변경한다. */
    @Transactional
    public TossPaymentService.TossPaymentResult cancelPaid(Long id, String reason) {
        CustomPayment cp = findById(id);
        if (cp.getStatus() != CustomPayment.Status.PAID
                || cp.getPaymentKey() == null || cp.getPaymentKey().isBlank()) {
            throw new IllegalStateException("토스로 결제 완료된 맞춤결제만 취소할 수 있습니다.");
        }
        TossPaymentService.TossPaymentResult result = tossPaymentService.cancelFull(cp.getPaymentKey(), reason);
        cp.setStatus(CustomPayment.Status.CANCELLED);
        return result;
    }

    /** 웹훅·관리자용: 토스 원장을 재조회해 맞춤결제 상태를 멱등 동기화한다. */
    @Transactional
    public ReconcileOutcome reconcileByOrderNumber(String orderNumber) {
        CustomPayment cp = customPaymentRepository.findByOrderNumber(orderNumber).orElse(null);
        if (cp == null) return ReconcileOutcome.PAYMENT_NOT_FOUND;

        TossPaymentService.TossPaymentResult ledger =
                cp.getPaymentKey() != null && !cp.getPaymentKey().isBlank()
                        ? tossPaymentService.getByPaymentKey(cp.getPaymentKey())
                        : tossPaymentService.getByOrderId(orderNumber);

        if (ledger.orderId() != null && !orderNumber.equals(ledger.orderId())) {
            return ReconcileOutcome.NO_CHANGE;
        }
        if (ledger.totalAmount() == null || !cp.getPrice().equals(ledger.totalAmount())) {
            return ReconcileOutcome.AMOUNT_MISMATCH;
        }
        if (TossPaymentService.STATUS_PARTIAL_CANCELED.equals(ledger.status())) {
            return ReconcileOutcome.PARTIAL_CANCEL_DETECTED;
        }
        if (ledger.isFullyCanceled()) {
            if (cp.getStatus() != CustomPayment.Status.CANCELLED) {
                cp.setStatus(CustomPayment.Status.CANCELLED);
                if (ledger.paymentKey() != null) cp.setPaymentKey(ledger.paymentKey());
                return ReconcileOutcome.CANCEL_SYNCED;
            }
            return ReconcileOutcome.NO_CHANGE;
        }
        if (ledger.isDone()) {
            if (cp.getStatus() == CustomPayment.Status.PENDING) {
                cp.setStatus(CustomPayment.Status.PAID);
                cp.setPaymentKey(ledger.paymentKey());
                cp.setPaidAt(java.time.LocalDateTime.now());
                return ReconcileOutcome.CONFIRMED_SYNCED;
            }
            return ReconcileOutcome.NO_CHANGE;
        }
        return ReconcileOutcome.NO_CHANGE;
    }

    // ─── 내부 유틸 ─────────────────────────────────────────────

    /** 새 파일이 있으면 업로드 후 URL 반환, 없으면 기존 URL 유지 */
    private String uploadIfPresent(MultipartFile file, String fallback) {
        if (file != null && !file.isEmpty()) {
            try {
                return fileStorageService.store(file);
            } catch (Exception e) {
                throw new IllegalArgumentException("이미지 업로드 실패: " + e.getMessage());
            }
        }
        return fallback;
    }
}
