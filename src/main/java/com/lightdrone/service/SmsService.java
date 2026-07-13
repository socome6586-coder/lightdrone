package com.lightdrone.service;

import lombok.extern.slf4j.Slf4j;
import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SmsService {

    private final DefaultMessageService messageService;
    private final String sender;
    private final boolean enabled;

    public SmsService(
            @Value("${sms.api-key:}") String apiKey,
            @Value("${sms.api-secret:}") String apiSecret,
            @Value("${sms.sender:010-3565-9741}") String sender) {

        this.sender = sender;
        this.enabled = apiKey != null && !apiKey.isBlank()
                && apiSecret != null && !apiSecret.isBlank();

        if (this.enabled) {
            this.messageService = NurigoApp.INSTANCE.initialize(apiKey, apiSecret, "https://api.coolsms.co.kr");
            log.info("[SMS] Coolsms 초기화 완료. 발신번호={}", sender);
        } else {
            this.messageService = null;
            log.warn("[SMS] API 키가 설정되지 않아 SMS 발송이 비활성화되어 있습니다.");
        }
    }

    public void send(String to, String content) {
        if (to == null || to.isBlank()) {
            log.warn("[SMS] 수신번호가 없어 발송을 건너뜁니다.");
            return;
        }

        if (!enabled) {
            String line = "=".repeat(60);
            System.out.println(line);
            System.out.println("[개발모드 SMS] TO=" + to);
            System.out.println("[개발모드 SMS] " + content);
            System.out.println(line);
            log.warn("[개발모드 SMS] TO={} | {}", to, content);
            return;
        }

        try {
            Message message = new Message();
            message.setFrom(sender.replace("-", ""));
            message.setTo(to.replace("-", ""));
            message.setText(content);

            messageService.sendOne(new SingleMessageSendingRequest(message));
            log.info("[SMS] 발송 성공 TO={}", to);
        } catch (Exception e) {
            log.error("[SMS] 발송 실패 TO={} : {}", to, e.getMessage());
        }
    }
}
