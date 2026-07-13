package com.lightdrone.service;

import com.lightdrone.domain.Product;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PurchasePolicyTest {

    @Test
    void acceptsAvailableProductWithinStock() {
        Product product = product(true, false, 3);

        assertThatCode(() -> PurchasePolicy.validate(product, 3)).doesNotThrowAnyException();
    }

    @Test
    void rejectsZeroAndNegativeQuantity() {
        Product product = product(true, false, 3);

        assertThatThrownBy(() -> PurchasePolicy.validate(product, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1개 이상");
        assertThatThrownBy(() -> PurchasePolicy.validate(product, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1개 이상");
    }

    @Test
    void rejectsUnavailableInquiryAndOutOfStockProducts() {
        assertThatThrownBy(() -> PurchasePolicy.validate(product(false, false, 3), 1))
                .hasMessageContaining("판매 중");
        assertThatThrownBy(() -> PurchasePolicy.validate(product(true, true, 3), 1))
                .hasMessageContaining("가격문의");
        assertThatThrownBy(() -> PurchasePolicy.validate(product(true, false, 3), 4))
                .hasMessageContaining("재고");
    }

    private Product product(boolean available, boolean priceOnRequest, int stock) {
        return Product.builder()
                .name("테스트 상품")
                .price(10_000L)
                .available(available)
                .priceOnRequest(priceOnRequest)
                .stock(stock)
                .build();
    }
}
