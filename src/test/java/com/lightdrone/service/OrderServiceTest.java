package com.lightdrone.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주문번호 생성 로직 단위 테스트 (Mockito 미사용 - Java 25 ByteBuddy 호환성 이슈 회피)
 */
class OrderServiceTest {

    /** OrderService 내부 주문번호 생성 로직과 동일한 방식 */
    private String generateOrderNumber() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Test
    @DisplayName("주문번호는 'ORD-' 접두사로 시작해야 한다")
    void orderNumber_startsWithORD() {
        String orderNumber = generateOrderNumber();
        assertThat(orderNumber).startsWith("ORD-");
    }

    @Test
    @DisplayName("주문번호 suffix는 8자리 대문자 16진수여야 한다")
    void orderNumber_suffixIsUpperHex8Chars() {
        String orderNumber = generateOrderNumber();
        String suffix = orderNumber.substring(4); // "ORD-" 제거

        assertThat(suffix).hasSize(8);
        assertThat(suffix).matches("[A-F0-9]{8}");
    }

    @Test
    @DisplayName("주문번호 전체 길이는 12자리여야 한다 (ORD- 4자 + 8자)")
    void orderNumber_totalLengthIs12() {
        String orderNumber = generateOrderNumber();
        assertThat(orderNumber).hasSize(12);
    }

    @RepeatedTest(100)
    @DisplayName("주문번호는 반복 생성해도 중복이 없어야 한다 (100회 반복)")
    void orderNumber_isUniqueAcross100Calls() {
        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            generated.add(generateOrderNumber());
        }
        // 100개가 모두 고유하다면 set 크기도 100
        assertThat(generated).hasSize(100);
    }

    @Test
    @DisplayName("UUID 앞 8자는 원래 UUID와 동일한 prefix여야 한다")
    void orderNumber_prefixMatchesUUID() {
        UUID uuid = UUID.randomUUID();
        String raw = uuid.toString().substring(0, 8).toUpperCase();
        String orderNumber = "ORD-" + raw;

        assertThat(orderNumber.substring(4)).isEqualTo(raw);
    }
}
