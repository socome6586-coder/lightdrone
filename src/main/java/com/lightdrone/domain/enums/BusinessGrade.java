package com.lightdrone.domain.enums;

/**
 * 회원 등급(사업자 회원 세부 등급).
 * NONE             : 일반 회원 (할인 없음)
 * WHOLESALE        : 도매 사업자 회원 (상시 할인)
 * SUPER_WHOLESALE  : 도도매 사업자 회원 (상시 할인)
 *
 * 실제 할인율은 고정값이 아니라 {@code BusinessGradePolicy}(전역 설정)에서
 * 관리자가 조정할 수 있다. 기본값은 도매 20%, 도도매 30%.
 */
public enum BusinessGrade {
    NONE("일반"),
    WHOLESALE("도매"),
    SUPER_WHOLESALE("도도매");

    private final String label;

    BusinessGrade(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 사업자 회원 여부 (할인 대상) */
    public boolean isBusiness() {
        return this != NONE;
    }
}
