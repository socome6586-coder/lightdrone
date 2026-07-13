package com.lightdrone.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP 기반 로그인 시도 횟수를 추적합니다.
 * 5회 실패 시 10분 동안 해당 IP의 로그인을 차단합니다.
 */
@Service
public class LoginAttemptService {

    private static final int  MAX_ATTEMPTS      = 5;
    private static final long BLOCK_DURATION_MS = 10 * 60_000L; // 10분

    private final ConcurrentHashMap<String, AttemptInfo> cache = new ConcurrentHashMap<>();

    /** 로그인 실패 시 호출 */
    public void loginFailed(String ip) {
        cache.compute(ip, (k, existing) -> {
            AttemptInfo info = (existing != null) ? existing : new AttemptInfo();
            info.recordFailure();
            return info;
        });
    }

    /** 로그인 성공 시 호출 — 해당 IP 기록 초기화 */
    public void loginSucceeded(String ip) {
        cache.remove(ip);
    }

    /** 해당 IP가 현재 차단 중인지 확인 */
    public boolean isBlocked(String ip) {
        AttemptInfo info = cache.get(ip);
        if (info == null || info.getFailures() < MAX_ATTEMPTS) return false;
        if (isExpired(info)) {
            cache.remove(ip);
            return false;
        }
        return true;
    }

    /** 차단 해제까지 남은 초 */
    public long getRemainingBlockSeconds(String ip) {
        AttemptInfo info = cache.get(ip);
        if (info == null || info.getBlockedAt() == 0) return 0;
        long elapsed = Instant.now().toEpochMilli() - info.getBlockedAt();
        return Math.max(0, (BLOCK_DURATION_MS - elapsed) / 1000);
    }

    private boolean isExpired(AttemptInfo info) {
        return info.getBlockedAt() > 0
                && Instant.now().toEpochMilli() - info.getBlockedAt() >= BLOCK_DURATION_MS;
    }

    // ── 내부 클래스 ──────────────────────────────────────────────────
    private static class AttemptInfo {
        private int  failures;
        private long blockedAt; // block 시작 타임스탬프(ms), 0 = 미차단

        void recordFailure() {
            failures++;
            if (failures >= MAX_ATTEMPTS && blockedAt == 0) {
                blockedAt = Instant.now().toEpochMilli();
            }
        }

        int  getFailures()  { return failures;  }
        long getBlockedAt() { return blockedAt; }
    }
}
