package com.lightdrone.domain.enums;

public enum RefundStatus {
    NONE("없음"),
    REQUESTED("환불/반품 신청"),
    PROCESSING("처리 중"),
    COMPLETED("환불 완료"),
    REJECTED("환불 거절");

    private final String label;
    RefundStatus(String label) { this.label = label; }
    public String getLabel() { return label; }
}
