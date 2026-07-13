package com.lightdrone.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightdrone.service.OrderService;
import com.lightdrone.service.CustomPaymentService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 토스페이먼츠 웹훅 수신 엔드포인트(요구 10·11).
 *
 * <p><b>신뢰 경계</b>: 웹훅 본문(JSON)은 식별자(orderId/paymentKey)를 꺼내는 용도로만 쓰고,
 * 본문에 실린 status·금액은 <b>신뢰하지 않는다</b>. 실제 상태는 {@link OrderService#reconcileByOrderNumber}
 * → 토스 조회 API 재조회 결과를 권위 있는 기준으로 사용한다(위·변조 웹훅 방어).
 *
 * <p><b>CSRF</b>: 외부(토스 서버)에서 토큰 없이 POST 하므로 SecurityConfig 에서
 * <b>정확히 이 경로만</b>(/api/payment/toss/webhook) CSRF 예외 처리한다.
 *
 * <p><b>응답 정책</b>: 정상 처리에만 200 을 반환한다. 일시적인 조회·DB 오류에는 503 을 보내
 * 토스의 재전송(최대 7회)을 활용한다. 동기화는 멱등(reconcile)이라 중복 웹훅에도 안전하다.
 */
@RestController
@RequestMapping("/api/payment/toss")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookController.class);

    private final OrderService orderService;
    private final CustomPaymentService customPaymentService;
    private final ObjectMapper objectMapper;

    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(@RequestBody(required = false) String rawBody) {
        try {
            String orderNumber = extractOrderId(rawBody);
            if (orderNumber == null) {
                log.warn("[토스 웹훅] orderId 를 본문에서 찾지 못함 — 무시");
                return ResponseEntity.ok("ignored");
            }
            // 본문 status 는 신뢰하지 않고, orderNumber 로 토스 원장 재조회 후 동기화
            OrderService.ReconcileOutcome outcome = orderService.reconcileByOrderNumber(orderNumber);
            log.info("[토스 웹훅] orderNumber={} 동기화 결과={}", orderNumber, outcome);
            if (outcome == OrderService.ReconcileOutcome.ORDER_NOT_FOUND) {
                CustomPaymentService.ReconcileOutcome customOutcome =
                        customPaymentService.reconcileByOrderNumber(orderNumber);
                log.info("[토스 웹훅] customOrderNumber={} 동기화 결과={}", orderNumber, customOutcome);
                if (customOutcome == CustomPaymentService.ReconcileOutcome.PAYMENT_NOT_FOUND) {
                    // 로컬 주문 커밋보다 웹훅이 먼저 도착했을 가능성이 있으므로 재전송 요청
                    return ResponseEntity.status(503).body("retry");
                }
            }
        } catch (Exception e) {
            log.error("[토스 웹훅] 처리 실패 — 재전송 요청: {}", e.getMessage());
            return ResponseEntity.status(503).body("retry");
        }
        return ResponseEntity.ok("ok");
    }

    /** 웹훅 페이로드에서 orderId 추출. 토스는 보통 data.orderId, 일부 포맷은 최상위 orderId. */
    String extractOrderId(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode data = root.path("data");
            if (data.hasNonNull("orderId")) return data.get("orderId").asText();
            if (root.hasNonNull("orderId")) return root.get("orderId").asText();
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
