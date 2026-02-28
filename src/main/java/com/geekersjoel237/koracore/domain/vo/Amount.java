package com.geekersjoel237.koracore.domain.vo;

import com.geekersjoel237.koracore.domain.exception.CurrencyMismatchException;

import java.math.BigDecimal;

public record Amount(BigDecimal value, String currency) {

    public Amount {
        if (value == null)
            throw new IllegalArgumentException("Amount value cannot be null");
        if (value.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("Amount value cannot be negative");
        if (currency == null || currency.isBlank())
            throw new IllegalArgumentException("Amount currency cannot be blank");
    }

    public static Amount of(BigDecimal value, String currency) {
        return new Amount(value, currency);
    }

    public Amount add(Amount other) {
        requireSameCurrency(other);
        return new Amount(this.value.add(other.value), this.currency);
    }

    public Amount subtract(Amount other) {
        requireSameCurrency(other);
        BigDecimal result = this.value.subtract(other.value);
        if (result.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException(
                    "Subtraction result cannot be negative: " + result);
        return new Amount(result, this.currency);
    }

    public boolean isGreaterThan(Amount other) {
        requireSameCurrency(other);
        return this.value.compareTo(other.value) > 0;
    }

    public boolean isGreaterThanOrEqual(Amount other) {
        requireSameCurrency(other);
        return this.value.compareTo(other.value) >= 0;
    }

    private void requireSameCurrency(Amount other) {
        if (!this.currency.equals(other.currency))
            throw new CurrencyMismatchException(this.currency, other.currency);
    }

    public boolean isStrictPositive(){
        return this.value.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean equals(Amount other){
        if (other == null) return false;
        return this.currency.equals(other.currency) && this.value.compareTo(other.value) == 0;
    }
}