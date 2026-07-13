package com.lightdrone.controller;

import com.lightdrone.service.SmsRateLimiter;
import com.lightdrone.service.SmsService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/verify/phone")
@RequiredArgsConstructor
public class PhoneVerificationController {

    private final SmsService smsService;
    private final SmsRateLimiter rateLimiter;

    private static final int OTP_EXPIRE_MINUTES = 3;
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final SecureRandom OTP_RANDOM = new SecureRandom();
    public static final String SESSION_KEY = "PHONE_OTP_";

    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> send(
            @RequestParam String phone,
            @RequestParam String type,
            HttpSession session) {

        String normalizedType = normalizeType(type);
        log.info("[PhoneVerify] /send called - type={}", normalizedType);

        if (!phone.matches("^01[0-9]{8,9}$")) {
            return badRequest("올바른 휴대폰 번호를 입력해 주세요. 예: 01012345678");
        }

        if (!rateLimiter.tryConsume(phone, normalizedType)) {
            int used = rateLimiter.getCount(phone, normalizedType);
            log.warn("[PhoneVerify] daily limit exceeded - phone={}, type={}, used={}", phone, normalizedType, used);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
                    Map.of(
                            "success", false,
                            "message", "오늘 인증번호 발송 횟수(" + SmsRateLimiter.DAILY_LIMIT + "회)를 초과했습니다. 내일 다시 시도해 주세요."
                    )
            );
        }

        String otp = String.format("%06d", OTP_RANDOM.nextInt(1_000_000));

        Map<String, Object> otpData = new HashMap<>();
        otpData.put("phone", phone);
        otpData.put("otp", otp);
        otpData.put("expiresAt", LocalDateTime.now().plusMinutes(OTP_EXPIRE_MINUTES));
        otpData.put("verified", false);
        otpData.put("attempts", 0);
        session.setAttribute(SESSION_KEY + normalizedType, otpData);

        smsService.send(phone, "[라이트드론] 인증번호: " + otp + " (3분 이내 입력해 주세요)");

        return ok("인증번호가 발송되었습니다. 3분 이내에 입력해 주세요.");
    }

    @PostMapping("/confirm")
    public ResponseEntity<Map<String, Object>> confirm(
            @RequestParam String phone,
            @RequestParam String otp,
            @RequestParam String type,
            HttpSession session) {

        String normalizedType = normalizeType(type);
        String sessionKey = SESSION_KEY + normalizedType;

        @SuppressWarnings("unchecked")
        Map<String, Object> otpData = (Map<String, Object>) session.getAttribute(sessionKey);

        if (otpData == null) {
            return badRequest("인증번호를 먼저 요청해 주세요.");
        }

        LocalDateTime expiresAt = (LocalDateTime) otpData.get("expiresAt");
        if (LocalDateTime.now().isAfter(expiresAt)) {
            session.removeAttribute(sessionKey);
            return badRequest("인증번호가 만료되었습니다. 다시 요청해 주세요.");
        }

        if (!phone.equals(otpData.get("phone"))) {
            return badRequest("휴대폰 번호가 일치하지 않습니다.");
        }

        if (!otp.equals(otpData.get("otp"))) {
            int attempts = ((Number) otpData.getOrDefault("attempts", 0)).intValue() + 1;
            if (attempts >= MAX_OTP_ATTEMPTS) {
                session.removeAttribute(sessionKey);
                return badRequest("인증번호 입력 횟수를 초과했습니다. 인증번호를 다시 요청해 주세요.");
            }
            otpData.put("attempts", attempts);
            session.setAttribute(sessionKey, otpData);
            return badRequest("인증번호가 올바르지 않습니다. 다시 확인해 주세요.");
        }

        otpData.put("verified", true);
        session.setAttribute(sessionKey, otpData);

        return ok("인증이 완료되었습니다.");
    }

    private String normalizeType(String type) {
        return type == null ? "" : type.trim().toUpperCase();
    }

    private ResponseEntity<Map<String, Object>> ok(String message) {
        return ResponseEntity.ok(Map.of("success", true, "message", message));
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        return ResponseEntity.badRequest()
                .body(Map.of("success", false, "message", message));
    }
}
