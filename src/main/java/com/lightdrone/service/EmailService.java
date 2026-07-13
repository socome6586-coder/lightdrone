package com.lightdrone.service;

import com.lightdrone.domain.Member;
import com.lightdrone.domain.Order;
import com.lightdrone.domain.enums.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class EmailService {

    private static final DateTimeFormatter WELCOME_DATE_FMT = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    private final RestClient brevo;
    private final String senderEmail;
    private final String senderName;
    private final boolean enabled;

    public EmailService(
            @Value("${brevo.api-key:}") String apiKey,
            @Value("${brevo.api-url:https://api.brevo.com/v3}") String apiUrl,
            @Value("${brevo.sender-email:}") String senderEmail,
            @Value("${brevo.sender-name:라이트드론}") String senderName) {
        this.senderEmail = senderEmail;
        this.senderName = senderName;
        this.enabled = apiKey != null && !apiKey.isBlank();
        this.brevo = RestClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("api-key", apiKey == null ? "" : apiKey)
                .defaultHeader("accept", "application/json")
                .build();

        if (!enabled) {
            log.warn("[메일] BREVO_API_KEY가 설정되지 않아 이메일 발송이 비활성화됩니다.");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void pingAccount() {
        brevo.get().uri("/account").retrieve().toBodilessEntity();
    }

    public boolean sendHtml(String toEmail, String subject, String htmlBody) {
        if (!enabled) {
            log.warn("[메일] 발송 비활성화 상태라 건너뜁니다. to={}, subject={}", toEmail, subject);
            return false;
        }
        if (toEmail == null || toEmail.isBlank()) {
            return false;
        }
        if (senderEmail == null || senderEmail.isBlank()) {
            log.warn("[메일] MAIL_SENDER_EMAIL이 설정되지 않아 발송을 건너뜁니다. to={}, subject={}", toEmail, subject);
            return false;
        }

        try {
            Map<String, Object> body = Map.of(
                    "sender", Map.of("name", senderName, "email", senderEmail),
                    "to", List.of(Map.of("email", toEmail)),
                    "subject", subject,
                    "htmlContent", htmlBody
            );

            brevo.post()
                    .uri("/smtp/email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("[메일] 발송 완료: to={}, subject={}", toEmail, subject);
            return true;
        } catch (Exception e) {
            log.warn("[메일] 발송 실패: to={}, subject={}, reason={}", toEmail, subject, e.getMessage(), e);
            return false;
        }
    }

    @Async
    public void sendOrderConfirmEmail(Order order) {
        if (order == null || order.getBuyerEmail() == null || order.getBuyerEmail().isBlank()) {
            return;
        }
        sendHtml(
                order.getBuyerEmail(),
                "[라이트드론] 주문이 접수되었습니다 - " + order.getOrderNumber(),
                buildOrderEmailHtml(order)
        );
    }

    @Async
    public void sendOrderStatusEmail(Order order) {
        if (order == null || order.getBuyerEmail() == null || order.getBuyerEmail().isBlank()) {
            return;
        }
        boolean isShipping = order.getStatus() == OrderStatus.SHIPPING;
        String subject = isShipping
                ? "[라이트드론] 주문하신 상품의 배송이 시작되었습니다 - " + order.getOrderNumber()
                : "[라이트드론] 배송이 완료되었습니다 - " + order.getOrderNumber();
        sendHtml(order.getBuyerEmail(), subject, buildStatusEmailHtml(order, isShipping));
    }

    @Async
    public void sendWelcomeEmail(Member member) {
        if (member == null || member.getEmail() == null || member.getEmail().isBlank()) {
            return;
        }
        sendHtml(
                member.getEmail(),
                "[라이트드론] 회원가입을 환영합니다.",
                buildWelcomeEmailHtml(member)
        );
    }

    private String buildStatusEmailHtml(Order order, boolean isShipping) {
        String title = isShipping ? "배송이 시작되었습니다." : "배송이 완료되었습니다.";
        String message = isShipping
                ? "주문하신 상품이 출발했습니다. 아래 배송 정보를 확인해 주세요."
                : "주문하신 상품의 배송이 완료되었습니다. 이용해 주셔서 감사합니다.";

        String trackingHtml = "";
        if (order.getTrackingNumber() != null && !order.getTrackingNumber().isBlank()) {
            trackingHtml = String.format(
                    """
                    <div style='margin-top:16px;padding:12px;background:#f5f8ff;border:1px solid #dbe8ff;font-size:14px;'>
                      <strong>택배사</strong> %s &nbsp;|&nbsp; <strong>운송장</strong> %s
                    </div>
                    """,
                    text(order.getCourierCompany()),
                    text(order.getTrackingNumber())
            );
        }

        return baseEmailHtml(
                title,
                String.format(
                        """
                        <p style='color:#555;line-height:1.7;'>안녕하세요, <strong>%s</strong>님.</p>
                        <p style='color:#555;line-height:1.7;'>주문번호 <strong>%s</strong>의 %s</p>
                        %s
                        %s
                        """,
                        text(order.getBuyerName()),
                        text(order.getOrderNumber()),
                        message,
                        trackingHtml,
                        contactBox()
                )
        );
    }

    private String buildOrderEmailHtml(Order order) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.KOREA);
        StringBuilder items = new StringBuilder();

        for (var item : order.getItems()) {
            items.append(String.format(
                    """
                    <tr>
                      <td style='padding:9px;border-bottom:1px solid #eee;'>%s</td>
                      <td style='padding:9px;border-bottom:1px solid #eee;text-align:center;'>%d개</td>
                      <td style='padding:9px;border-bottom:1px solid #eee;text-align:right;'>%s원</td>
                    </tr>
                    """,
                    text(item.getProductName()),
                    item.getQuantity(),
                    nf.format(item.getTotalPrice())
            ));
        }

        long shippingFee = order.getShippingFee() != null ? order.getShippingFee() : Order.DEFAULT_SHIPPING_FEE;

        String content = String.format(
                """
                <p style='color:#555;line-height:1.7;'>안녕하세요, <strong>%s</strong>님.</p>
                <p style='color:#555;line-height:1.7;'>
                  주문번호 <strong>%s</strong>가 정상적으로 접수되었습니다.<br>
                  결제 및 배송 진행 상황은 이메일 또는 마이페이지에서 확인하실 수 있습니다.
                </p>

                <h4 style='border-bottom:2px solid #111;padding-bottom:8px;color:#222;margin-top:24px;'>주문 상품</h4>
                <table style='width:100%%;border-collapse:collapse;font-size:14px;'>
                  <thead>
                    <tr style='background:#f5f5f5;'>
                      <th style='padding:9px;text-align:left;'>상품명</th>
                      <th style='padding:9px;text-align:center;'>수량</th>
                      <th style='padding:9px;text-align:right;'>금액</th>
                    </tr>
                  </thead>
                  <tbody>%s</tbody>
                  <tfoot>
                    <tr>
                      <td colspan='2' style='padding:8px;text-align:right;color:#777;'>배송비</td>
                      <td style='padding:8px;text-align:right;color:#777;'>%s원</td>
                    </tr>
                    <tr>
                      <td colspan='2' style='padding:12px 8px;text-align:right;font-weight:bold;'>총 결제금액</td>
                      <td style='padding:12px 8px;text-align:right;font-weight:bold;color:#0d6efd;font-size:16px;'>%s원</td>
                    </tr>
                  </tfoot>
                </table>

                <h4 style='border-bottom:2px solid #111;padding-bottom:8px;color:#222;margin-top:24px;'>배송 정보</h4>
                <table style='width:100%%;font-size:14px;color:#555;'>
                  <tr><td style='padding:5px 0;width:30%%;color:#888;'>수령인</td><td>%s</td></tr>
                  <tr><td style='padding:5px 0;color:#888;'>연락처</td><td>%s</td></tr>
                  <tr><td style='padding:5px 0;color:#888;'>주소</td><td>%s %s</td></tr>
                  <tr><td style='padding:5px 0;color:#888;'>결제 수단</td><td>%s</td></tr>
                </table>

                %s
                """,
                text(order.getBuyerName()),
                text(order.getOrderNumber()),
                items,
                nf.format(shippingFee),
                nf.format(order.getTotalPrice()),
                text(order.getReceiverName()),
                text(order.getReceiverPhone()),
                text(order.getAddress()),
                text(order.getAddressDetail()),
                order.getPaymentMethod() != null ? order.getPaymentMethod().getLabel() : "-",
                contactBox()
        );

        return baseEmailHtml("주문이 접수되었습니다.", content);
    }

    private String buildWelcomeEmailHtml(Member member) {
        String signupAt = member.getCreatedAt() != null
                ? member.getCreatedAt().format(WELCOME_DATE_FMT)
                : "-";

        String content = String.format(
                """
                <p style='color:#333;font-size:15px;line-height:1.7;'>
                  안녕하세요, <strong>%s</strong>님.<br>
                  라이트드론 회원가입이 정상적으로 완료되었습니다.
                </p>

                <div style='margin-top:20px;padding:16px 18px;background:#f7f9fc;border:1px solid #e2e6ef;'>
                  <div style='font-size:14px;color:#666;margin-bottom:8px;'>가입 정보</div>
                  <div style='font-size:14px;color:#222;line-height:1.8;'>
                    <div><strong>아이디</strong> : %s</div>
                    <div><strong>가입일</strong> : %s</div>
                  </div>
                </div>

                <p style='margin-top:22px;color:#666;font-size:14px;line-height:1.7;'>
                  앞으로 라이트드론의 상품과 서비스를 편하게 이용해 주세요.<br>
                  문의가 필요하시면 고객센터 010-3565-9741로 연락해 주세요.
                </p>
                """,
                text(member.getName()),
                text(member.getUsername()),
                signupAt
        );

        return baseEmailHtml("회원가입을 환영합니다.", content);
    }

    private String baseEmailHtml(String title, String content) {
        return String.format(
                """
                <div style='font-family:Apple SD Gothic Neo,Malgun Gothic,Arial,sans-serif;max-width:600px;margin:0 auto;background:#f5f6f8;padding:20px;'>
                  <div style='background:#111827;color:white;padding:24px;text-align:center;'>
                    <h2 style='margin:0;font-size:22px;letter-spacing:1px;'>LIGHT DRONE</h2>
                    <p style='margin:8px 0 0;font-size:15px;opacity:0.9;'>%s</p>
                  </div>
                  <div style='background:white;padding:26px;border:1px solid #e5e7eb;border-top:0;'>
                    %s
                  </div>
                </div>
                """,
                title,
                content
        );
    }

    private String contactBox() {
        return """
                <div style='margin-top:24px;padding:15px;background:#f7f9fc;border:1px solid #e2e6ef;font-size:13px;color:#555;line-height:1.7;'>
                  문의사항은 <strong>010-3565-9741</strong> 또는 홈페이지 문의를 이용해 주세요.
                </div>
                """;
    }

    private String text(String value) {
        return value == null ? "" : value;
    }
}
