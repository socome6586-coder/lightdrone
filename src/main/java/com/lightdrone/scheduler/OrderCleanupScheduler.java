package com.lightdrone.scheduler;

import com.lightdrone.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 결제창을 닫는 등으로 성공/실패 콜백이 모두 오지 않아 방치된 카드결제 주문을
 * 주기적으로 자동 취소하고 재고를 복원한다. (재고가 영구히 묶이는 문제 방지)
 */
@Component
@RequiredArgsConstructor
public class OrderCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderCleanupScheduler.class);

    /** 생성 후 이 시간(분)이 지나도록 결제가 완료되지 않은 카드 주문을 만료 처리 */
    private static final int EXPIRE_MINUTES = 30;

    private final OrderService orderService;

    /** 앱 기동 2분 후부터 10분 간격으로 실행 */
    @Scheduled(fixedDelay = 600_000L, initialDelay = 120_000L)
    public void expireStalePendingPayments() {
        try {
            int count = orderService.expireStalePendingPayments(EXPIRE_MINUTES);
            if (count > 0) {
                log.info("미결제 카드주문 자동취소 {}건 (재고 복원 완료)", count);
            }
        } catch (Exception e) {
            log.warn("미결제 주문 자동취소 처리 중 오류", e);
        }
    }
}
