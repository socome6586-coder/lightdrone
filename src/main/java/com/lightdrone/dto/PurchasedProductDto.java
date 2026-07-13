package com.lightdrone.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 회원이 실제 구매한 제품 (후기 작성 시 선택용) */
@Getter
@AllArgsConstructor
public class PurchasedProductDto {
    private final Long id;
    private final String name;
    private final String imageUrl;
}
