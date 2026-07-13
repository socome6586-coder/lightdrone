package com.lightdrone.service;

import com.lightdrone.domain.Product;

/** 주문과 장바구니에서 공통으로 적용하는 구매 가능 여부 규칙입니다. */
final class PurchasePolicy {

    private PurchasePolicy() {
    }

    static void validate(Product product, int quantity) {
        if (product == null) {
            throw new IllegalArgumentException("상품을 찾을 수 없습니다.");
        }
        if (!product.isAvailable()) {
            throw new IllegalArgumentException("판매 중인 상품이 아닙니다.");
        }
        if (product.isPriceOnRequest()) {
            throw new IllegalArgumentException("가격문의요망 상품은 직접 구매할 수 없습니다. 문의 후 구매해주세요.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("구매 수량은 1개 이상이어야 합니다.");
        }
        if (product.getStock() < quantity) {
            throw new IllegalArgumentException("재고가 부족합니다.");
        }
    }
}
