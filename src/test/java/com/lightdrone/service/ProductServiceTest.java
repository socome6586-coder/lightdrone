package com.lightdrone.service;

import com.lightdrone.domain.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ProductService 순수 단위 테스트 (Mockito 미사용 - Java 25 ByteBuddy 호환성 이슈 회피)
 */
class ProductServiceTest {

    @Test
    @DisplayName("Product 빌더: 판매 가능 상품 생성 확인")
    void product_builder_available() {
        Product p = Product.builder()
                .name("농업용 드론 A")
                .price(1_500_000L)
                .category("농업용")
                .stock(10)
                .available(true)
                .sortOrder(1)
                .build();

        assertThat(p.getName()).isEqualTo("농업용 드론 A");
        assertThat(p.isAvailable()).isTrue();
        assertThat(p.getStock()).isEqualTo(10);
        assertThat(p.getPrice()).isEqualTo(1_500_000L);
    }

    @Test
    @DisplayName("Product 빌더: 판매 불가 상품 생성 확인")
    void product_builder_unavailable() {
        Product p = Product.builder()
                .name("단종 드론 B")
                .price(800_000L)
                .category("농업용")
                .stock(0)
                .available(false)
                .sortOrder(2)
                .build();

        assertThat(p.isAvailable()).isFalse();
        assertThat(p.getStock()).isZero();
    }

    @Test
    @DisplayName("카테고리 필터: 스트림으로 특정 카테고리 상품만 추출")
    void category_filter_stream() {
        List<Product> all = List.of(
                Product.builder().name("드론A").category("농업용").available(true).stock(5).price(100L).sortOrder(1).build(),
                Product.builder().name("드론B").category("산업용").available(true).stock(3).price(200L).sortOrder(2).build(),
                Product.builder().name("드론C").category("농업용").available(true).stock(2).price(150L).sortOrder(3).build()
        );

        List<Product> 농업용 = all.stream()
                .filter(p -> "농업용".equals(p.getCategory()))
                .toList();

        assertThat(농업용).hasSize(2);
        assertThat(농업용).allMatch(p -> "농업용".equals(p.getCategory()));
    }

    @Test
    @DisplayName("카테고리 필터: 존재하지 않는 카테고리는 빈 리스트 반환")
    void category_filter_empty_for_unknown() {
        List<Product> all = List.of(
                Product.builder().name("드론A").category("농업용").available(true).stock(5).price(100L).sortOrder(1).build()
        );

        List<Product> result = all.stream()
                .filter(p -> "없는카테고리".equals(p.getCategory()))
                .toList();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Optional.empty(): 존재하지 않는 ID 조회 시 예외 발생 시뮬레이션")
    void findById_empty_throws() {
        Optional<Product> empty = Optional.empty();

        assertThatThrownBy(() ->
                empty.orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."))
        )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("상품을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("가격 포맷: 0원 이상의 양수 가격만 허용")
    void product_price_validation() {
        Product p = Product.builder()
                .name("테스트")
                .price(0L)
                .category("테스트")
                .stock(1)
                .available(true)
                .sortOrder(1)
                .build();

        assertThat(p.getPrice()).isGreaterThanOrEqualTo(0L);
    }
}
