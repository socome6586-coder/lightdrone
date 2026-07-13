package com.lightdrone.service;

import com.lightdrone.domain.Order;
import com.lightdrone.domain.OrderItem;
import com.lightdrone.domain.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * 관리자에게 보내는 알림(SMS)을 한 곳으로 모은 서비스.
 * <p>
 * application.yml 의 {@code admin.phones}(쉼표 구분)에 등록된 모든 번호로
 * 비동기 발송한다. 새 문의 알림·재고 알림 등 여러 곳에서 재사용한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAlertService {

    private final SmsService smsService;

    /** 관리자 알림 수신 번호 목록 (쉼표 구분) */
    @Value("${admin.phones:}")
    private String adminPhones;

    /**
     * 등록된 모든 관리자 번호로 알림 SMS를 비동기 발송한다.
     * 번호가 설정돼 있지 않으면 조용히 건너뛴다.
     */
    public void sendToAdmins(String message) {
        if (adminPhones == null || adminPhones.isBlank()) {
            log.warn("[SMS] admin.phones 미설정 — 관리자 알림 SMS를 건너뜁니다.");
            return;
        }
        for (String phone : adminPhones.split(",")) {
            String trimmed = phone.trim();
            if (!trimmed.isBlank()) {
                log.info("[SMS] 관리자 알림 발송 → {}", trimmed);
                CompletableFuture.runAsync(() -> smsService.send(trimmed, message));
            }
        }
    }

    /**
     * 주문 알림 SMS를 발송한다. 원가/할인/택배비를 분류해서 보여준다.
     * <p>
     * 전달되는 {@code order}는 items + product 가 fetch-join 되어 있어야 한다
     * (OSIV off 환경 → {@code OrderService#findByIdWithItems} 사용 권장).
     *
     * @param order       상품(product)까지 로딩된 주문
     * @param statusLabel 예: "신규 주문", "카드결제 완료 주문"
     */
    public void sendOrderAlert(Order order, String statusLabel) {
        String itemName = order.getItems().isEmpty() ? "상품" : order.getItems().get(0).getProductName();
        if (order.getItems().size() > 1) itemName += " 외 " + (order.getItems().size() - 1) + "건";

        long originalSum = 0;   // 정가 합계
        long saleSum = 0;       // 실제 판매가(할인 적용) 합계
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            long unitOriginal = (product != null && product.getPrice() != null)
                    ? product.getPrice() : item.getProductPrice();
            originalSum += unitOriginal * item.getQuantity();
            saleSum += item.getTotalPrice();
        }

        long discountAmount = originalSum - saleSum;
        long shippingFee = order.getShippingFee() != null ? order.getShippingFee() : 0L;

        StringBuilder msg = new StringBuilder();
        msg.append("[라이트드론] ").append(statusLabel).append("\n")
           .append("주문번호: ").append(order.getOrderNumber()).append("\n")
           .append("주문자: ").append(order.getBuyerName()).append("\n")
           .append("상품: ").append(itemName).append("\n")
           .append("원래가격: ").append(String.format("%,d", originalSum)).append("원\n");

        if (discountAmount > 0 && originalSum > 0) {
            int discountPercent = (int) Math.round(discountAmount * 100.0 / originalSum);
            msg.append("할인: ").append(discountPercent).append("% (-")
               .append(String.format("%,d", discountAmount)).append("원)\n")
               .append("상품금액: ").append(String.format("%,d", saleSum)).append("원\n");
        } else {
            msg.append("상품금액: ").append(String.format("%,d", saleSum)).append("원\n");
        }

        msg.append("택배비: ").append(String.format("%,d", shippingFee)).append("원\n")
           .append("합계: ").append(String.format("%,d", order.getTotalPrice())).append("원");

        sendToAdmins(msg.toString());
    }
}
