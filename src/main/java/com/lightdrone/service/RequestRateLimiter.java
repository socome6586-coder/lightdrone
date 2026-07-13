package com.lightdrone.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RequestRateLimiter {

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public boolean tryConsume(String scope, HttpServletRequest request, int maxRequests, Duration window) {
        String key = scope + ":" + resolveClientIp(request);
        long now = Instant.now().toEpochMilli();
        long windowMs = window.toMillis();
        int[] allowed = {0};

        buckets.compute(key, (k, bucket) -> {
            Bucket current = bucket;
            if (current == null || now - current.windowStartedAt >= windowMs) {
                current = new Bucket(now, 0);
            }
            if (current.count < maxRequests) {
                current.count++;
                allowed[0] = 1;
            }
            return current;
        });

        return allowed[0] == 1;
    }

    @Scheduled(fixedDelay = 600_000L)
    public void cleanup() {
        long cutoff = Instant.now().minus(Duration.ofHours(1)).toEpochMilli();
        buckets.entrySet().removeIf(entry -> entry.getValue().windowStartedAt < cutoff);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static class Bucket {
        private final long windowStartedAt;
        private int count;

        private Bucket(long windowStartedAt, int count) {
            this.windowStartedAt = windowStartedAt;
            this.count = count;
        }
    }
}
