package com.geekersjoel237.koracore.payment.domain.enums;

public enum PaymentMethod {
    CARD("CARD"),
    ORANGE_MONEY("OM"),
    MOBILE_MONEY("MOMO"),
    WALLET("WALLET");

    private final String value;

    PaymentMethod(String value) {
        this.value = value;
    }

    public static PaymentMethod fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Error, cannot accept empty payment method !");
        }
        String normalized = value.trim().toUpperCase();
        for (PaymentMethod method : values()) {
            if (method.name().equals(normalized) || method.value.equals(normalized)) {
                return method;
            }
        }
        throw new IllegalArgumentException("Unknown payment method: " + value);
    }

    public String value() {
        return this.value;
    }
}