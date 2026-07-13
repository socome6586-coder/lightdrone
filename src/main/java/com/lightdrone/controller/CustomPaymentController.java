package com.lightdrone.controller;

import com.lightdrone.domain.CustomPayment;
import com.lightdrone.service.CustomPaymentService;
import com.lightdrone.service.MemberService;
import com.lightdrone.service.RequestRateLimiter;
import com.lightdrone.service.TossPaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@Controller
@RequestMapping("/custom")
@RequiredArgsConstructor
public class CustomPaymentController {

    private static final int CUSTOM_LOOKUP_LIMIT_PER_MINUTE = 20;
    private static final Duration CUSTOM_LOOKUP_WINDOW = Duration.ofMinutes(1);

    private final CustomPaymentService customPaymentService;
    private final TossPaymentService tossPaymentService;
    private final MemberService memberService;
    private final RequestRateLimiter requestRateLimiter;

    @Value("${toss.payments.client-key}")
    private String tossClientKey;

    /** 맞춤결제 코드 입력 페이지 */
    @GetMapping
    public String index(Model model) {
        model.addAttribute("tossClientKey", tossClientKey);
        model.addAttribute("searched", false);
        return "custom-payment";
    }

    /** 코드로 본인 결제 건 조회 */
    @PostMapping("/lookup")
    public String lookup(@RequestParam String code,
                         HttpServletRequest request,
                         Model model) {
        model.addAttribute("tossClientKey", tossClientKey);
        model.addAttribute("searchedCode", code);
        if (!requestRateLimiter.tryConsume("custom-payment-lookup", request,
                CUSTOM_LOOKUP_LIMIT_PER_MINUTE, CUSTOM_LOOKUP_WINDOW)) {
            model.addAttribute("errorMsg", "조회 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
            model.addAttribute("searched", true);
            return "custom-payment";
        }
        model.addAttribute("results", customPaymentService.findByCode(code));
        model.addAttribute("searched", true);
        return "custom-payment";
    }

    /** 결제 시작 — 주문번호 생성 후 토스 결제에 필요한 정보 반환 (AJAX) */
    @PostMapping("/prepare")
    @ResponseBody
    public ResponseEntity<?> prepare(@RequestParam Long id,
                                     @AuthenticationPrincipal UserDetails userDetails) {
        try {
            Long memberId = (userDetails != null)
                    ? memberService.findByUsername(userDetails.getUsername()).getId() : null;
            CustomPayment cp = customPaymentService.prepareForPayment(id, memberId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "orderNumber", cp.getOrderNumber(),
                    "amount", cp.getPrice(),
                    "orderName", "맞춤결제 - " + cp.getName(),
                    "buyerName", cp.getName()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /** 토스 결제 성공 콜백 */
    @GetMapping("/success")
    public String success(@RequestParam String paymentKey,
                          @RequestParam String orderId,
                          @RequestParam Long amount,
                          Model model) {
        try {
            CustomPayment cp = customPaymentService.findByOrderNumber(orderId);

            // 멱등 단축경로: 같은 결제건으로 이미 PAID 면 토스 재호출 없이 완료 화면 표시
            if (cp.getStatus() == CustomPayment.Status.PAID
                    && paymentKey.equals(cp.getPaymentKey())) {
                model.addAttribute("payment", cp);
                return "custom-payment-complete";
            }

            if (!cp.getPrice().equals(amount)) {
                throw new IllegalStateException("결제 금액이 일치하지 않습니다.");
            }
            // 서버 보유 금액 기준으로 토스 승인 + 응답 대조(요구 3)
            tossPaymentService.confirm(paymentKey, orderId, cp.getPrice());
            customPaymentService.markPaid(orderId, paymentKey);

            model.addAttribute("payment", customPaymentService.findByOrderNumber(orderId));
            return "custom-payment-complete";
        } catch (Exception e) {
            return "redirect:/custom/fail?message="
                    + java.net.URLEncoder.encode(e.getMessage(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /** 토스 결제 실패/취소 콜백 */
    @GetMapping("/fail")
    public String fail(@RequestParam(required = false) String code,
                       @RequestParam(required = false) String message,
                       Model model) {
        model.addAttribute("errorCode", code);
        model.addAttribute("errorMessage", message != null ? message : "결제가 취소되었습니다.");
        return "payment-fail";
    }
}
