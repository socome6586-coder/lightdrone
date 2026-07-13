package com.lightdrone.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

/**
 * 비회원(게스트) 장바구니 항목.
 *
 * DB에 저장하지 않고 HTTP 세션에 보관한다(로그인 사용자는 cart_items 테이블 사용).
 * 로그인 시 {@code GuestCartService.mergeIntoMemberCart()} 로 회원 장바구니에 병합된다.
 */
@Getter
@Setter
@NoArgsConstructor
public class GuestCartItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 세션 내 항목 식별용 로컬 ID (수량변경/삭제 시 사용) */
    private long lineId;

    private Long productId;

    private int quantity = 1;

    /** 로그인 병합 시 재검증을 위한 원본 선택 옵션 ID 목록 */
    private List<Long> optionIds;

    /** 표시용 대표 옵션 ID */
    private Long selectedOptionId;

    /** 표시용 옵션 조합명 스냅샷 */
    private String selectedOptionName;

    /** 표시용 옵션 추가금액 스냅샷 */
    private Long selectedOptionExtraPrice = 0L;
}
