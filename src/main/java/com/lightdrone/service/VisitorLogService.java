package com.lightdrone.service;

import com.lightdrone.domain.VisitorLog;
import com.lightdrone.repository.VisitorLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VisitorLogService {

    private final VisitorLogRepository visitorLogRepository;

    @Transactional
    public void record(String ip) {
        String hash = sha256(ip);
        LocalDate today = LocalDate.now();
        if (!visitorLogRepository.existsByVisitDateAndIpHash(today, hash)) {
            visitorLogRepository.save(VisitorLog.builder()
                    .visitDate(today)
                    .ipHash(hash)
                    .build());
        }
    }

    public long countTotal() {
        return visitorLogRepository.count();
    }

    public long countToday() {
        return visitorLogRepository.countByVisitDate(LocalDate.now());
    }

    /** 최근 N일간 날짜별 방문자 수 (날짜 → 방문자 수) */
    public Map<String, Long> getDailyVisitors(int days) {
        LocalDate start = LocalDate.now().minusDays(days - 1L);
        List<Object[]> rows = visitorLogRepository.countByDateSince(start);

        // 날짜 범위 전체를 0으로 초기화
        Map<String, Long> result = new LinkedHashMap<>();
        for (int i = days - 1; i >= 0; i--) {
            result.put(LocalDate.now().minusDays(i).toString(), 0L);
        }
        for (Object[] row : rows) {
            result.put(row[0].toString(), (Long) row[1]);
        }
        return result;
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return input;
        }
    }
}
