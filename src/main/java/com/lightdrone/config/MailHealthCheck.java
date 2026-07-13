package com.lightdrone.config;

import com.lightdrone.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MailHealthCheck {

    private final EmailService emailService;

    @EventListener(ApplicationReadyEvent.class)
    public void verifyMailApi() {
        if (!emailService.isEnabled()) {
            log.warn("[메일 점검] BREVO_API_KEY가 설정되지 않아 이메일 발송이 비활성화되어 있습니다.");
            return;
        }
        try {
            emailService.pingAccount();
            log.info("[메일 점검] Brevo API 인증 성공. 이메일 발송 가능 상태입니다.");
        } catch (Exception e) {
            log.error("[메일 점검] Brevo API 인증 실패. reason={}", e.getMessage(), e);
            log.error("[메일 점검] BREVO_API_KEY 값과 Brevo 발신자 인증 상태를 확인해 주세요.");
        }
    }
}
