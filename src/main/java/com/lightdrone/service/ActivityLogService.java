package com.lightdrone.service;

import com.lightdrone.domain.ActivityLog;
import com.lightdrone.repository.ActivityLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 활동(감사) 로그 기록·조회 서비스.
 * 기록은 요청 처리가 끝난 뒤(인터셉터 afterCompletion) 별도 트랜잭션으로 저장하며,
 * 로깅 실패가 실제 작업에 영향을 주지 않도록 예외를 삼킨다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;

    @Transactional
    public void record(String adminUsername, String menu, String action,
                       String method, String requestUri, String description, String ip) {
        try {
            activityLogRepository.save(ActivityLog.builder()
                    .adminUsername(adminUsername)
                    .menu(menu)
                    .action(action)
                    .method(method)
                    .requestUri(truncate(requestUri, 300))
                    .description(truncate(description, 300))
                    .ipAddress(ip)
                    .build());
        } catch (Exception e) {
            log.warn("[활동로그] 기록 실패 (uri={})", requestUri, e);
        }
    }

    @Transactional(readOnly = true)
    public Page<ActivityLog> getLogs(Pageable pageable) {
        return activityLogRepository.findAllByOrderByCreatedAtDescIdDesc(pageable);
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
