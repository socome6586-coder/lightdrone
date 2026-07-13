package com.lightdrone.domain.enums;

public enum PaymentMethod {
    BANK_TRANSFER("무통장입금"),
    CARD("카드결제");

    private final String label;
    PaymentMethod(String label) { this.label = label; }
    public String getLabel() { return label; }
}
