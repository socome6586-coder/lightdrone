package com.lightdrone.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 동일 전화번호에 대한 SMS 인증번호 일일 발송 횟수 제한.
 * type(SIGNUP / PROFILE 등) 별로 독립적으로 집계하며, 각각 하루 최대 5회.
 *
 * <p>ConcurrentHashMap.compute()의 원자성을 이용해 별도 락 없이 스레드-세이프하게 동작한다.
 * 날짜가 바뀌면 카운트가 자동 초기화되며, 자정마다 오래된 항목을 정리한다.
 */
@Slf4j
@Component
public class SmsRateLimiter {

    public static final int DAILY_LIMIT = 5;

    /**
     * key: "정규화된전화번호:TYPE"
     * value: int[2] → [0] = 오늘 날짜(epochDay), [1] = 발송 횟수
     */
    private final ConcurrentHashMap<String, int[]> store = new ConcurrentHashMap<>();

    /**
     * 발송 가능 여부를 확인하고, 가능하면 카운트를 1 증가시킨다.
     *
     * @param phone 전화번호 (하이픈 유무 무관)
     * @param type  인증 목적 (SIGNUP, PROFILE 등)
     * @return 발송 허용이면 true, 일일 한도 초과면 false
     */
    public boolean tryConsume(String phone, String type) {
        String key = normalize(phone) + ":" + type.toUpperCase();
        int todayEpoch = (int) LocalDate.now().toEpochDay();
        int[] flag = {0};

        store.compute(key, (k, v) -> {
            // 오늘 첫 요청이거나 날짜가 바뀌었으면 카운트 초기화
            int[] entry = (v == null || v[0] != todayEpoch)
                    ? new int[]{todayEpoch, 0}
                    : v;
            if (entry[1] < DAILY_LIMIT) {
                entry[1]++;
                flag[0] = 1;   // 발송 허용
            }
            return entry;
        });

        if (flag[0] == 0) {
            log.warn("[SmsRateLimiter] 일일 한도 초과 - phone={}, type={}", mask(phone), type);
        }
        return flag[0] == 1;
    }

    /**
     * 현재까지 오늘 발송한 횟수 조회 (로그·응답 메시지 생성용).
     */
    public int getCount(String phone, String type) {
        String key = normalize(phone) + ":" + type.toUpperCase();
        int todayEpoch = (int) LocalDate.now().toEpochDay();
        int[] v = store.get(key);
        return (v != null && v[0] == todayEpoch) ? v[1] : 0;
    }

    /** 자정마다 전날 항목 일괄 삭제 */
    @Scheduled(cron = "0 0 0 * * *")
    public void cleanup() {
        int todayEpoch = (int) LocalDate.now().toEpochDay();
        int before = store.size();
        store.entrySet().removeIf(e -> e.getValue()[0] < todayEpoch);
        log.info("[SmsRateLimiter] cleanup: {} → {} entries", before, store.size());
    }

    /** 하이픈·공백 제거 → 숫자만 */
    private String normalize(String phone) {
        return (phone == null) ? "" : phone.replaceAll("[^0-9]", "");
    }

    /** 로그에서 번호 마스킹 (예: 01012345678 → 010****5678) */
    private String mask(String phone) {
        String n = normalize(phone);
        return n.length() >= 7 ? n.substring(0, 3) + "****" + n.substring(n.length() - 4) : "***";
    }
}
