package com.lightdrone.controller;

import com.lightdrone.domain.Order;
import com.lightdrone.service.AdminAlertService;
import com.lightdrone.service.EmailService;
import com.lightdrone.service.OrderService;
import com.lightdrone.service.SmsService;
import com.lightdrone.service.TossPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final TossPaymentService tossPaymentService;
    private final OrderService orderService;
    private final EmailService emailService;
    private final SmsService smsService;
    private final AdminAlertService adminAlertService;

    /** 토스페이먼츠 결제 성공 콜백 */
    @GetMapping("/success")
    public String success(@RequestParam String paymentKey,
                          @RequestParam String orderId,
                          @RequestParam Long amount,
                          Model model) {
        boolean tossConfirmed = false;
        try {
            Order order = orderService.findByOrderNumber(orderId);

            // 멱등 단축경로: 새로고침/중복 콜백으로 이미 같은 결제건이 승인된 주문이면
            // 토스 재호출·SMS·이메일 없이 완료 페이지로 보낸다(요구 4 — 중복 부수효과 방지).
            if (paymentKey.equals(order.getPaymentKey())
                    && order.getStatus() != com.lightdrone.domain.enums.OrderStatus.PENDING_PAYMENT
                    && order.getStatus() != com.lightdrone.domain.enums.OrderStatus.CANCELLED
                    && order.getStatus() != com.lightdrone.domain.enums.OrderStatus.REJECTED) {
                return "redirect:/order/complete/" + orderId;
            }

            // 금액 검증 — 서버 보유 주문금액(클라 파라미터 신뢰 금지) 기준
            if (!order.getTotalPrice().equals(amount)) {
                throw new IllegalStateException("결제 금액이 일치하지 않습니다.");
            }

            // 토스 결제 승인 API 호출 — 응답의 status/orderId/paymentKey/금액을 모두 대조(요구 3)
            TossPaymentService.TossPaymentResult result =
                    tossPaymentService.confirm(paymentKey, orderId, order.getTotalPrice());
            tossConfirmed = true;

            // 주문 상태 PAID 변경 + 승인 금액·상태 저장 (멱등)
            OrderService.PaymentConfirmOutcome outcome = orderService.confirmPayment(
                    order.getId(), paymentKey, result.totalAmount(), result.status());

            // 이미 승인된 멱등 재요청이면 알림 발송 생략
            if (outcome == OrderService.PaymentConfirmOutcome.ALREADY_CONFIRMED) {
                return "redirect:/order/complete/" + orderId;
            }

            // 최신 주문 정보 로드
            Order confirmedOrder = orderService.findByOrderNumber(orderId);

            // 주문 확인 이메일 발송 (신규 승인일 때만)
            try { emailService.sendOrderConfirmEmail(confirmedOrder); }
            catch (Exception ignored) {}

            // 구매 완료 SMS 발송 (신규 승인일 때만)
            try { sendOrderCompleteSms(confirmedOrder); }
            catch (Exception ignored) {}

            try { sendAdminOrderAlert(confirmedOrder); }
            catch (Exception ignored) {}

            return "redirect:/order/complete/" + orderId;

        } catch (Exception e) {
            // 토스 승인이 끝난 뒤 DB 반영만 실패한 경우에는 실패 콜백으로 보내 주문/재고를
            // 자동취소하면 안 된다. 웹훅 또는 관리자 '토스 내역 재조회'로 복구한다.
            if (tossConfirmed) {
                model.addAttribute("errorCode", "PAYMENT_SYNC_PENDING");
                model.addAttribute("errorMessage",
                        "결제 승인은 완료되었지만 주문 반영이 지연되고 있습니다. 중복 결제하지 마시고 고객센터로 문의해주세요.");
                return "payment-fail";
            }
            return "redirect:/payment/fail?code=CONFIRM_FAILED&message="
                    + java.net.URLEncoder.encode(e.getMessage(), java.nio.charset.StandardCharsets.UTF_8)
                    + "&orderId=" + orderId;
        }
    }

    /** 구매 완료 SMS 발송 */
    private void sendOrderCompleteSms(Order order) {
        String itemName = order.getItems().isEmpty() ? "상품" : order.getItems().get(0).getProductName();
        if (order.getItems().size() > 1) itemName += " 외 " + (order.getItems().size() - 1) + "건";

        String formattedPrice = String.format("%,d", order.getTotalPrice());
        String msg = "[라이트드론] 결제가 완료되었습니다.\n"
                   + "주문번호: " + order.getOrderNumber() + "\n"
                   + "상품: " + itemName + "\n"
                   + "결제금액: " + formattedPrice + "원\n"
                   + "비회원조회: 로그인화면 하단\n"
                   + "문의: 010-3565-9741";
        smsService.send(order.getBuyerPhone(), msg);
    }

    private void sendAdminOrderAlert(Order order) {
        // 원가/할인 계산에는 product 가 필요하므로 fetch-join 으로 다시 로드 (OSIV off)
        Order full = orderService.findByIdWithItems(order.getId());
        adminAlertService.sendOrderAlert(full, "카드결제 완료 주문");
    }

    /** 토스페이먼츠 결제 실패/취소 콜백 */
    @GetMapping("/fail")
    public String fail(@RequestParam(required = false) String code,
                       @RequestParam(required = false) String message,
                       @RequestParam(required = false) String orderId,
                       Model model) {

        // 생성된 주문이 있으면 취소 처리 (재고 복원)
        if (orderId != null && !orderId.isBlank()) {
            try {
                Order order = orderService.findByOrderNumber(orderId);
                orderService.cancel(order.getId());
            } catch (Exception ignored) {}
        }

        model.addAttribute("errorCode", code);
        model.addAttribute("errorMessage",
                message != null ? message : "결제가 취소되었습니다.");
        return "payment-fail";
    }
}
