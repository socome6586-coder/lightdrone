package com.lightdrone.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 주문으로 재고가 차감될 때 품절/재고 부족을 관리자에게 알린다.
 * <p>
 * 매 주문마다 알림을 보내면 스팸이 되므로, 임계치 경계를 '처음 넘는' 순간에만
 * 1회 알림한다. (예: 임계치 5개 → 재고가 5 이상에서 5 미만으로 떨어질 때만)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockAlertService {

    private final AdminAlertService adminAlertService;

    /** 이 수량 미만으로 떨어지면 '재고 부족' 알림 (application.yml: stock.low-threshold) */
    @Value("${stock.low-threshold:5}")
    private int lowThreshold;

    /** 재고 부족 임계치 (관리자 대시보드 재고 부족 위젯에서 동일 기준 사용) */
    public int getLowThreshold() {
        return lowThreshold;
    }

    /**
     * 재고 차감 직후 호출한다. 차감 전/후 재고를 비교해 경계를 처음 넘을 때만 알림한다.
     *
     * @param productName 상품명
     * @param stockBefore 차감 전 재고
     * @param stockAfter  차감 후 재고
     */
    public void checkAfterDecrement(String productName, int stockBefore, int stockAfter) {
        try {
            // 품절: 재고가 양수에서 0 이하로 처음 떨어진 순간
            if (stockBefore > 0 && stockAfter <= 0) {
                adminAlertService.sendToAdmins(
                        "[라이트드론] 품절 알림\n'" + productName + "' 상품이 품절되었습니다. (재고 0)\n"
                                + "관리자 페이지에서 재고를 확인해 주세요.");
                return;
            }
            // 재고 부족: 임계치 이상 → 임계치 미만으로 처음 떨어진 순간 (품절은 위에서 처리)
            if (stockBefore >= lowThreshold && stockAfter < lowThreshold && stockAfter > 0) {
                adminAlertService.sendToAdmins(
                        "[라이트드론] 재고 부족 알림\n'" + productName + "' 상품 재고가 "
                                + stockAfter + "개 남았습니다. (알림 기준 " + lowThreshold + "개 미만)\n"
                                + "관리자 페이지에서 재고를 확인해 주세요.");
            }
        } catch (Exception e) {
            // 알림 실패가 주문 처리를 막아선 안 된다
            log.warn("[재고알림] 발송 처리 중 오류 (상품={})", productName, e);
        }
    }
}
